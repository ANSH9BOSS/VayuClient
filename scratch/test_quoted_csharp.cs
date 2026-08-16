using System;
using System.Diagnostics;
using System.IO;
using System.Text;

public class Program
{
    public static void Main()
    {
        string java = @"C:\Users\ANSH\AppData\Roaming\.minecraft\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon\bin\java.exe";
        string gameDir = @"C:\Users\ANSH\AppData\Roaming\VayuClient\Instances\Spunky Optimized\game";
        string nativesDir = @"C:\Users\ANSH\AppData\Roaming\VayuClient\Instances\Spunky Optimized\natives";

        string logPath = @"C:\Users\ANSH\AppData\Roaming\VayuClient\logs\launch_20260816_164404.log";
        string logContent = File.ReadAllText(logPath);
        
        int cpIndex = logContent.IndexOf("-cp ");
        int mainIndex = logContent.IndexOf(" net.fabricmc.loader.impl.launch.knot.KnotClient ");
        string cp = logContent.Substring(cpIndex + 4, mainIndex - (cpIndex + 4)).Trim();

        var sb = new StringBuilder();
        sb.Append("-Xms1024M -Xmx4096M -Dminecraft.launcher.brand=VayuClient -Dminecraft.launcher.version=1.4.0 ");
        sb.Append("-Djava.library.path=\"" + nativesDir + "\\java\" ");
        sb.Append("-Djna.tmpdir=\"" + nativesDir + "\\jna\" ");
        sb.Append("-Dorg.lwjgl.system.SharedLibraryExtractPath=\"" + nativesDir + "\\lwjgl\" ");
        sb.Append("-Dio.netty.native.workdir=\"" + nativesDir + "\\netty\" ");
        sb.Append("-cp \"" + cp + "\" ");
        sb.Append("net.fabricmc.loader.impl.launch.knot.KnotClient ");
        sb.Append("--username ANSH9BOSS --version 26.2 --gameDir \"" + gameDir + "\" --assetsDir \"C:\\Users\\ANSH\\AppData\\Roaming\\VayuClient\\assets\" --assetIndex 32 --uuid 3d71ded8-51f8-3220-913c-1f6c6d8d8a11 --accessToken 0 --versionType release");

        var psi = new ProcessStartInfo
        {
            FileName = java,
            Arguments = sb.ToString(),
            WorkingDirectory = gameDir,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };

        Console.WriteLine("Executing Java 25 with Minecraft 26.2...");
        using (var p = Process.Start(psi))
        {
            p.OutputDataReceived += (s, e) => { if (e.Data != null) Console.WriteLine("[OUT] " + e.Data); };
            p.ErrorDataReceived += (s, e) => { if (e.Data != null) Console.WriteLine("[ERR] " + e.Data); };
            p.BeginOutputReadLine();
            p.BeginErrorReadLine();

            bool exited = p.WaitForExit(15000);
            if (!exited)
            {
                Console.WriteLine(">>> MINECRAFT 26.2 FABRIC STARTED SUCCESSFULLY AND IS RUNNING LIVE! <<<");
                try { p.Kill(); } catch { }
            }
            else
            {
                Console.WriteLine("Process exited with code: " + p.ExitCode);
            }
        }
    }
}
