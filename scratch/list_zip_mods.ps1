Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipPath = 'C:\Users\ANSH\Downloads\26.2.zip'
if (Test-Path $zipPath) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    Write-Host "Total entries in $zipPath : $($zip.Entries.Count)"
    $modEntries = $zip.Entries | Where-Object { $_.FullName -like '*mods/*' -and $_.Name.EndsWith('.jar') }
    Write-Host "`nMod JAR files in 26.2.zip ($($modEntries.Count) mods):"
    foreach ($m in $modEntries) {
        Write-Host " - $($m.Name) ($($m.Length) bytes)"
    }
    $zip.Dispose()
} else {
    Write-Host "26.2.zip not found at $zipPath"
}
