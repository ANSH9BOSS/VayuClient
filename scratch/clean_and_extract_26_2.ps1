Add-Type -AssemblyName System.IO.Compression.FileSystem
$modsDir = 'C:\Users\ANSH\AppData\Roaming\vayuclient\Instances\Spunky Optimized\game\mods'
$zipPath = 'C:\Users\ANSH\Downloads\26.2.zip'

Write-Host "1. Cleaning stale / conflicting mods in $modsDir..."
if (Test-Path $modsDir) {
    Get-ChildItem -Path $modsDir -File | Remove-Item -Force
    Write-Host "Cleared existing mods directory."
} else {
    New-Item -ItemType Directory -Path $modsDir -Force | Out-Null
}

Write-Host "2. Extracting fresh 26.2 mods from $zipPath..."
$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
$count = 0
foreach ($entry in $zip.Entries) {
    if ($entry.FullName -like '*mods/*' -and $entry.Name.EndsWith('.jar')) {
        $dest = Join-Path $modsDir $entry.Name
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
        $count++
        Write-Host " + Extracted: $($entry.Name)"
    }
}
$zip.Dispose()
Write-Host "`nSuccessfully extracted $count modern 26.2 mods into Spunky Optimized instance!"
