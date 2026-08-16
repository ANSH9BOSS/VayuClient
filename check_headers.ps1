$pngBytes = [System.IO.File]::ReadAllBytes("VayuClient\Assets\Images\vayu_logo.png")
Write-Host "PNG header: $($pngBytes[0..7] -join ' ')"
Write-Host "PNG size: $($pngBytes.Length) bytes"

$icoBytes = [System.IO.File]::ReadAllBytes("VayuClient\Assets\Images\vayu_logo.ico")
Write-Host "ICO header: $($icoBytes[0..15] -join ' ')"
Write-Host "ICO size: $($icoBytes.Length) bytes"
