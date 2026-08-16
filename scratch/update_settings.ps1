$settingsPath = "$env:APPDATA\VayuClient\settings.json"
if (Test-Path $settingsPath) {
    $s = Get-Content $settingsPath -Raw | ConvertFrom-Json
    $s | Add-Member -NotePropertyName "DiscordClientId" -NotePropertyValue "1538504622652661830" -Force
    $json = $s | ConvertTo-Json -Depth 4
    Set-Content -Path $settingsPath -Value $json -Force
    Write-Host "Updated $settingsPath to 1538504622652661830"
}
