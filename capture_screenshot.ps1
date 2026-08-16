Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$exePath = 'c:\Users\ANSH\.gemini\antigravity-ide\scratch\VayuClient\VayuClient\bin\Release\net8.0-windows\VayuClient.exe'
$proc = Start-Process -FilePath $exePath -PassThru
Start-Sleep -Seconds 3

$screen = [System.Windows.Forms.Screen]::PrimaryScreen
$bounds = $screen.Bounds
$bitmap = New-Object System.Drawing.Bitmap($bounds.Width, $bounds.Height)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)

$artifactDir = 'C:\Users\ANSH\.gemini\antigravity-ide\brain\93d2db72-d6e0-4e75-97a1-f362713edc6f'
$screenshotPath = Join-Path $artifactDir 'vayu_client_v120_verified.png'
$bitmap.Save($screenshotPath, [System.Drawing.Imaging.ImageFormat]::Png)

$graphics.Dispose()
$bitmap.Dispose()

Write-Host "Screenshot saved to: $screenshotPath"
Start-Sleep -Seconds 1
Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
