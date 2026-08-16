$appIds = @(
    "383226320970055681",
    "425630040713068544",
    "683883870634672152",
    "942095908902998016",
    "1249767220558135327",
    "1219665675485413446",
    "1121087858880192532",
    "837651034444562472",
    "734360341144076329",
    "809795058776899605"
)

foreach ($appId in $appIds) {
    try {
        $pipe = New-Object System.IO.Pipes.NamedPipeClientStream(".", "discord-ipc-0", [System.IO.Pipes.PipeDirection]::InOut, [System.IO.Pipes.PipeOptions]::Asynchronous)
        $pipe.Connect(300)
        
        $handshake = '{"v":1,"client_id":"' + $appId + '"}'
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($handshake)
        
        $writer = New-Object System.IO.BinaryWriter($pipe)
        $writer.Write([int]0)
        $writer.Write([int]$bytes.Length)
        $writer.Write($bytes)
        $writer.Flush()

        $buffer = New-Object byte[] 8192
        $asyncResult = $pipe.BeginRead($buffer, 0, $buffer.Length, $null, $null)
        if ($asyncResult.AsyncWaitHandle.WaitOne(1000)) {
            $readCount = $pipe.EndRead($asyncResult)
            if ($readCount -ge 8) {
                $op = [System.BitConverter]::ToInt32($buffer, 0)
                $len = [System.BitConverter]::ToInt32($buffer, 4)
                $json = [System.Text.Encoding]::UTF8.GetString($buffer, 8, $readCount - 8)
                if ($op -eq 1) {
                    Write-Host ">>> FOUND ACTIVE VALID DISCORD APP ID: $appId <<<"
                    Write-Host "Response: $json"
                } else {
                    Write-Host "Invalid: $appId (Opcode: $op)"
                }
            }
        }
        $pipe.Dispose()
    }
    catch {
    }
}
