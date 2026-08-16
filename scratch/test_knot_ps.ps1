$java = "C:\Users\ANSH\AppData\Roaming\.minecraft\runtime\java-runtime-delta\windows-x64\java-runtime-delta\bin\java.exe"
$gameDir = "C:\Users\ANSH\AppData\Roaming\VayuClient\Instances\Spunky Optimized\game"
$nativesDir = "C:\Users\ANSH\AppData\Roaming\VayuClient\Instances\Spunky Optimized\natives"

$logPath = "C:\Users\ANSH\AppData\Roaming\VayuClient\logs\launch_20260816_164404.log"
$logContent = Get-Content $logPath -Raw

$cpIndex = $logContent.IndexOf("-cp ")
$mainIndex = $logContent.IndexOf(" net.fabricmc.loader.impl.launch.knot.KnotClient ")
$cp = $logContent.Substring($cpIndex + 4, $mainIndex - ($cpIndex + 4)).Trim()

$cmd = "-Xms1024M -Xmx4096M -Dminecraft.launcher.brand=VayuClient -Dminecraft.launcher.version=1.4.0 `"-Djava.library.path=$nativesDir\java`" `"-Djna.tmpdir=$nativesDir\jna`" `"-Dorg.lwjgl.system.SharedLibraryExtractPath=$nativesDir\lwjgl`" `"-Dio.netty.native.workdir=$nativesDir\netty`" -cp `"$cp`" net.fabricmc.loader.impl.launch.knot.KnotClient --username ANSH9BOSS --version 26.2 --gameDir `"$gameDir`" --assetsDir `"C:\Users\ANSH\AppData\Roaming\VayuClient\assets`" --assetIndex 32 --uuid 3d71ded8-51f8-3220-913c-1f6c6d8d8a11 --accessToken 0 --versionType release"

Write-Host "Running Minecraft with quoted property flags..."
$proc = Start-Process -FilePath $java -ArgumentList $cmd -WorkingDirectory $gameDir -PassThru -NoNewWindow
Start-Sleep -Seconds 6
if (!$proc.HasExited) {
    Write-Host "SUCCESS: Minecraft / KnotClient is running actively with PID $($proc.Id)!" -ForegroundColor Green
    Stop-Process -Id $proc.Id -Force
} else {
    Write-Host "Process exited with code: $($proc.ExitCode)" -ForegroundColor Red
}
