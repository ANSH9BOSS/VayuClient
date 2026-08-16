$java = "C:\Users\ANSH\AppData\Roaming\.minecraft\runtime\java-runtime-delta\windows-x64\java-runtime-delta\bin\java.exe"
Write-Host "Testing Java with path with spaces..."

# Test 1: -Dkey="value with spaces"
Write-Host "Test 1: -Dkey=`"value with spaces`""
& $java "-Dtest.path=C:\Some Path With Spaces\dir" -version 2>&1

# Test 2: ProcessStartInfo with ArgumentList
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $java
$psi.ArgumentList.Add("-Dtest.path=C:\Some Path With Spaces\dir")
$psi.ArgumentList.Add("-version")
$psi.RedirectStandardError = $true
$psi.UseShellExecute = $false
$proc = [System.Diagnostics.Process]::Start($psi)
$err = $proc.StandardError.ReadToEnd()
$proc.WaitForExit()
Write-Host "ProcessStartInfo output: $err"
