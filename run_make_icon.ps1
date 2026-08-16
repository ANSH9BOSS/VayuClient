Add-Type -AssemblyName System.Drawing
$code = Get-Content 'MakeIcon.cs' -Raw
Add-Type -TypeDefinition $code -ReferencedAssemblies System.Drawing
[IconGenerator.Program]::Main(@())
