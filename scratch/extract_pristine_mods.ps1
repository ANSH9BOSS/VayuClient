Add-Type -AssemblyName System.IO.Compression.FileSystem
$modsDir = 'C:\Users\ANSH\AppData\Roaming\vayuclient\Instances\Spunky Optimized\game\mods'
$zipPath = 'C:\Users\ANSH\Downloads\26.2.zip'

Write-Host "1. Clearing $modsDir..."
if (Test-Path $modsDir) {
    Get-ChildItem -Path $modsDir -File | Remove-Item -Force
} else {
    New-Item -ItemType Directory -Path $modsDir -Force | Out-Null
}

Write-Host "2. Extracting only genuine mods (ignoring .fabric cache)..."
$zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
$count = 0
foreach ($entry in $zip.Entries) {
    # Match entries that are in a 'mods/' directory and NOT in '.fabric/'
    if ($entry.FullName -match '(?i)(^|/)mods/[^/]+\.jar$' -and $entry.FullName -notmatch '(?i)\.fabric') {
        $dest = Join-Path $modsDir $entry.Name
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
        $count++
        Write-Host " + [$count] $($entry.Name)"
    }
}
$zip.Dispose()
Write-Host "`nExtracted $count pristine mods into Spunky Optimized!"
