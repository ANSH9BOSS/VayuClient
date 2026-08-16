Add-Type -AssemblyName System.IO.Compression.FileSystem
$modsDir = 'C:\Users\ANSH\AppData\Roaming\vayuclient\Instances\Spunky Optimized\game\mods'
$jars = Get-ChildItem -Path $modsDir -Filter *.jar

$modIdMap = @{}
$duplicates = @()

foreach ($jar in $jars) {
    try {
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
        $modJsonEntry = $zip.GetEntry("fabric.mod.json")
        if ($modJsonEntry) {
            $reader = New-Object System.IO.StreamReader($modJsonEntry.Open())
            $content = $reader.ReadToEnd()
            $reader.Close()
            $json = $content | ConvertFrom-Json
            $id = $json.id
            $version = $json.version
            if ($modIdMap.ContainsKey($id)) {
                $duplicates += [PSCustomObject]@{
                    ModId = $id
                    ExistingFile = $modIdMap[$id].FileName
                    ExistingVersion = $modIdMap[$id].Version
                    NewFile = $jar.Name
                    NewVersion = $version
                }
            } else {
                $modIdMap[$id] = @{
                    FileName = $jar.Name
                    Version = $version
                    FullName = $jar.FullName
                }
            }
        }
        $zip.Dispose()
    } catch {
        Write-Warning "Failed parsing $($jar.Name): $_"
    }
}

Write-Host "`nTotal Unique Fabric Mods: $($modIdMap.Count)"
Write-Host "Duplicate Mod IDs: $($duplicates.Count)"
foreach ($d in $duplicates) {
    Write-Host "Duplicate Mod: $($d.ModId)"
    Write-Host "   Existing: $($d.ExistingFile) (v$($d.ExistingVersion))"
    Write-Host "   Duplicate: $($d.NewFile) (v$($d.NewVersion))"
}
