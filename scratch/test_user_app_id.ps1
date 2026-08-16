$appId = "1538504622652661830"
Write-Host "Testing Discord Application ID: $appId"

try {
    $pipe = New-Object System.IO.Pipes.NamedPipeClientStream(".", "discord-ipc-0", [System.IO.Pipes.PipeDirection]::InOut, [System.IO.Pipes.PipeOptions]::Asynchronous)
    $pipe.Connect(500)
    Write-Host "[1/3] Connected to local discord-ipc-0 named pipe."
    
    $handshake = '{"v":1,"client_id":"' + $appId + '"}'
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($handshake)
    
    $writer = New-Object System.IO.BinaryWriter($pipe)
    $writer.Write([int]0)
    $writer.Write([int]$bytes.Length)
    $writer.Write($bytes)
    $writer.Flush()
    Write-Host "[2/3] Handshake sent."
    
    $buffer = New-Object byte[] 8192
    $asyncResult = $pipe.BeginRead($buffer, 0, $buffer.Length, $null, $null)
    if ($asyncResult.AsyncWaitHandle.WaitOne(3000)) {
        $readCount = $pipe.EndRead($asyncResult)
        if ($readCount -ge 8) {
            $op = [System.BitConverter]::ToInt32($buffer, 0)
            $len = [System.BitConverter]::ToInt32($buffer, 4)
            $json = [System.Text.Encoding]::UTF8.GetString($buffer, 8, $readCount - 8)
            Write-Host "[3/3] Received Opcode $op from Discord: $json"
            
            if ($op -eq 1) {
                Write-Host "`n>>> SUCCESS! Discord accepted VayuClient App ID: $appId! <<<" -ForegroundColor Green
                
                # Test dispatching live activity
                $startTime = [long][DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
                $activity = @{
                    cmd = "SET_ACTIVITY"
                    args = @{
                        pid = [System.Diagnostics.Process]::GetCurrentProcess().Id
                        activity = @{
                            details = "In Launcher • Managing Instances & Mods"
                            state = "Owned & Developed by ANSH9BOSS"
                            timestamps = @{
                                start = $startTime
                            }
                            assets = @{
                                large_image = "vayu_logo"
                                large_text = "VayuClient v1.4.0"
                                small_image = "vayu_logo"
                                small_text = "Developer: ANSH9BOSS"
                            }
                        }
                    }
                    nonce = [System.Guid]::NewGuid().ToString()
                }
                
                $actJson = $activity | ConvertTo-Json -Depth 6 -Compress
                $actBytes = [System.Text.Encoding]::UTF8.GetBytes($actJson)
                $writer.Write([int]1)
                $writer.Write([int]$actBytes.Length)
                $writer.Write($actBytes)
                $writer.Flush()
                Write-Host "Dispatched live VayuClient presence activity to Discord!" -ForegroundColor Cyan
                Start-Sleep -Seconds 1
            }
        }
    }
    $pipe.Dispose()
} catch {
    Write-Host "Error testing Discord pipe: $_" -ForegroundColor Red
}
