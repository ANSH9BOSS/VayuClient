Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public class ShellNotify
{
    [DllImport("shell32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    public static extern void SHChangeNotify(uint wEventId, uint uFlags, IntPtr dwItem1, IntPtr dwItem2);

    public static void RefreshIconCache()
    {
        // SHCNE_ASSOCCHANGED = 0x08000000, SHCNF_IDLIST = 0x0000
        SHChangeNotify(0x08000000, 0x0000, IntPtr.Zero, IntPtr.Zero);
    }
}
"@

[ShellNotify]::RefreshIconCache()
Write-Host "Windows Shell and Icon Cache notification dispatched successfully!"
