$bytes = [System.IO.File]::ReadAllBytes('VayuClient\Assets\Images\vayu_logo.ico')
Write-Host "ICO File Size: $($bytes.Length) bytes"
$type = [BitConverter]::ToUInt16($bytes, 2)
$count = [BitConverter]::ToUInt16($bytes, 4)
Write-Host "Type: $type (1=ICO), Image Count: $count"
for ($i = 0; $i -lt $count; $i++) {
    $offset = 6 + $i * 16
    $w = [int]$bytes[$offset]
    $h = [int]$bytes[$offset + 1]
    if ($w -eq 0) { $w = 256 }
    if ($h -eq 0) { $h = 256 }
    $bpp = [BitConverter]::ToUInt16($bytes, $offset + 6)
    $imgSize = [BitConverter]::ToUInt32($bytes, $offset + 8)
    Write-Host ("  Frame " + $i + ": " + $w + "x" + $h + " (" + $bpp + " bpp), Size=" + $imgSize)
}
