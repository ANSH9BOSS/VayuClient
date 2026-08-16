using System;
using System.Diagnostics;
using System.IO;

public class Program
{
    public static void Main()
    {
        string java = @"C:\Users\ANSH\AppData\Roaming\.minecraft\runtime\java-runtime-delta\windows-x64\java-runtime-delta\bin\java.exe";
        var psi = new ProcessStartInfo
        {
            FileName = java,
            Arguments = "-Dtest.path=\"C:\\Some Path With Spaces\\dir\" -version",
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true
        };

        using (var p = Process.Start(psi))
        {
            p.WaitForExit();
            string err = p.StandardError.ReadToEnd();
            Console.WriteLine("Arguments Result:");
            Console.WriteLine(err);
        }
    }
}
