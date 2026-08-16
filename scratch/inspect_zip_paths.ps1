Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead('C:\Users\ANSH\Downloads\26.2.zip')
$allEntries = $zip.Entries | Select-Object -ExpandProperty FullName
Write-Host "Total entries: $($allEntries.Count)"
Write-Host "`nDirectories in zip:"
$allEntries | ForEach-Object { ($_.Split('/')[0]) } | Select-Object -Unique | ForEach-Object { Write-Host " - $_" }

Write-Host "`nAll mod-like jars:"
$modJars = $zip.Entries | Where-Object { $_.Name.EndsWith('.jar') }
foreach ($m in $modJars) {
    Write-Host " Entry: $($m.FullName)"
}
$zip.Dispose()
