try {
    $pipe = New-Object System.IO.Pipes.NamedPipeClientStream(".", "discord-ipc-0", [System.IO.Pipes.PipeDirection]::InOut, [System.IO.Pipes.PipeOptions]::Asynchronous)
    $pipe.Connect(500)

    # 1. Handshake
    $appId = "356875570916753438"
    $handshake = '{"v":1,"client_id":"' + $appId + '"}'
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($handshake)
    
    $writer = New-Object System.IO.BinaryWriter($pipe)
    $writer.Write([int]0)
    $writer.Write([int]$bytes.Length)
    $writer.Write($bytes)
    $writer.Flush()

    # Read Handshake READY
    $buffer = New-Object byte[] 8192
    $asyncResult = $pipe.BeginRead($buffer, 0, $buffer.Length, $null, $null)
    if ($asyncResult.AsyncWaitHandle.WaitOne(2000)) {
        $readCount = $pipe.EndRead($asyncResult)
        $op = [System.BitConverter]::ToInt32($buffer, 0)
        $len = [System.BitConverter]::ToInt32($buffer, 4)
        $json = [System.Text.Encoding]::UTF8.GetString($buffer, 8, $readCount - 8)
        Write-Host "Handshake response: $json"
    }

    # 2. Send SET_ACTIVITY
    $startTime = [int64]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
    $activityObj = @{
        cmd = "SET_ACTIVITY"
        args = @{
            pid = [System.Diagnostics.Process]::GetCurrentProcess().Id
            activity = @{
                details = "VayuClient • In Launcher"
                state = "Owned & Developed by ANSH9BOSS"
                timestamps = @{
                    start = $startTime
                }
                assets = @{
                    large_image = "minecraft"
                    large_text = "VayuClient v1.3.2"
                    small_image = "grass"
                    small_text = "Developer: ANSH9BOSS"
                }
            }
        }
        nonce = [System.Guid]::NewGuid().ToString("N")
    }

    $actJson = $activityObj | ConvertTo-Json -Depth 6 -Compress
    $actBytes = [System.Text.Encoding]::UTF8.GetBytes($actJson)

    $writer.Write([int]1) # Opcode 1 = FRAME
    $writer.Write([int]$actBytes.Length)
    $writer.Write($actBytes)
    $writer.Flush()

    # Read Activity Response
    $asyncResult2 = $pipe.BeginRead($buffer, 0, $buffer.Length, $null, $null)
    if ($asyncResult2.AsyncWaitHandle.WaitOne(2000)) {
        $readCount2 = $pipe.EndRead($asyncResult2)
        $op2 = [System.BitConverter]::ToInt32($buffer, 0)
        $len2 = [System.BitConverter]::ToInt32($buffer, 4)
        $json2 = [System.Text.Encoding]::UTF8.GetString($buffer, 8, $readCount2 - 8)
        Write-Host "`nActivity Response:"
        Write-Host $json2
    }

    $pipe.Dispose()
}
catch {
    Write-Host "Error: $($_.Exception.Message)"
}
