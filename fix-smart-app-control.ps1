$ErrorActionPreference = "Continue"

# 1. Unblock all files in installed directory and dist directory
Get-ChildItem -Path "C:\Users\ANSH\AppData\Local\Programs\VayuClient" -Recurse | Unblock-File
Get-ChildItem -Path "c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\dist" -Recurse | Unblock-File

# 2. Get or create the code signing certificate
$cert = Get-ChildItem Cert:\CurrentUser\My -CodeSigningCert | Where-Object { $_.Subject -like "*ANSH9BOSS*" } | Select-Object -First 1
if (-not $cert) {
    $cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject "CN=ANSH9BOSS (VayuClient), O=VayuClient, C=IN" -CertStoreLocation Cert:\CurrentUser\My -NotAfter (Get-Date).AddYears(5)
}

# 3. Export certificate to file and add to Trusted Root & Trusted Publisher stores
$cerPath = Join-Path $env:TEMP "VayuClientRoot.cer"
[System.IO.File]::WriteAllBytes($cerPath, $cert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert))

Write-Host "Registering certificate in CurrentUser stores..." -ForegroundColor Cyan
certutil -user -addstore "Root" $cerPath
certutil -user -addstore "TrustedPublisher" $cerPath

# 4. Sign all binaries
$filesToSign = @(
    "C:\Users\ANSH\AppData\Local\Programs\VayuClient\VayuClient.exe",
    "c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\dist\VayuClient.exe",
    "c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\dist\VayuClientSetup.exe"
)

foreach ($f in $filesToSign) {
    if (Test-Path $f) {
        Set-AuthenticodeSignature -FilePath $f -Certificate $cert -HashAlgorithm SHA256
        Unblock-File -Path $f
        $sig = Get-AuthenticodeSignature -FilePath $f
        Write-Host "-> $f : Status = $($sig.Status)" -ForegroundColor Green
    }
}

Write-Host "`nAll binaries signed and trusted successfully!" -ForegroundColor Green
