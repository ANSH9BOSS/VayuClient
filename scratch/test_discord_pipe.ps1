$appIds = @("743272956062597192", "450489886121984000", "812403756262916127", "100000000000000000")

foreach ($appId in $appIds) {
    Write-Host "`nTesting AppId: $appId"
    try {
        $pipe = New-Object System.IO.Pipes.NamedPipeClientStream(".", "discord-ipc-0", [System.IO.Pipes.PipeDirection]::InOut, [System.IO.Pipes.PipeOptions]::Asynchronous)
        $pipe.Connect(500)
        
        $handshake = '{"v":1,"client_id":"' + $appId + '"}'
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($handshake)
        
        $writer = New-Object System.IO.BinaryWriter($pipe)
        $writer.Write([int]0)
        $writer.Write([int]$bytes.Length)
        $writer.Write($bytes)
        $writer.Flush()

        $buffer = New-Object byte[] 8192
        $asyncResult = $pipe.BeginRead($buffer, 0, $buffer.Length, $null, $null)
        if ($asyncResult.AsyncWaitHandle.WaitOne(1500)) {
            $readCount = $pipe.EndRead($asyncResult)
            if ($readCount -ge 8) {
                $op = [System.BitConverter]::ToInt32($buffer, 0)
                $len = [System.BitConverter]::ToInt32($buffer, 4)
                $json = [System.Text.Encoding]::UTF8.GetString($buffer, 8, $readCount - 8)
                Write-Host "Success! Opcode: $op, Length: $len"
                Write-Host "Response: $json"
            } else {
                Write-Host "Pipe closed by Discord (Read $readCount bytes)"
            }
        } else {
            Write-Host "Read timed out"
        }
        $pipe.Dispose()
    }
    catch {
        Write-Host "Error: $($_.Exception.Message)"
    }
}
