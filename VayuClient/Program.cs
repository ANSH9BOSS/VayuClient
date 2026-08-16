using System;
using System.Linq;
using System.Runtime.InteropServices;
using VayuClient.Core;
using VayuClient.QA;

namespace VayuClient
{
    public static class Program
    {
        [DllImport("kernel32.dll")]
        private static extern bool AttachConsole(int dwProcessId);
        private const int ATTACH_PARENT_PROCESS = -1;

        [STAThread]
        public static void Main(string[] args)
        {
            if (args.Length > 0 && args.Any(a => a.Equals("--version", StringComparison.OrdinalIgnoreCase) || a.Equals("-v", StringComparison.OrdinalIgnoreCase)))
            {
                AttachConsole(ATTACH_PARENT_PROCESS);
                Console.WriteLine($"{AppInfo.AppName} {AppInfo.FullVersionName} by {AppInfo.DeveloperName}");
                Environment.Exit(0);
                return;
            }

            if (args.Length > 0 && args.Any(a => a.Equals("--qa", StringComparison.OrdinalIgnoreCase) || a.Equals("/qa", StringComparison.OrdinalIgnoreCase)))
            {
                AttachConsole(ATTACH_PARENT_PROCESS);
                int exitCode = QaRunner.RunQaAsync(args).GetAwaiter().GetResult();
                Environment.Exit(exitCode);
                return;
            }

            if (args.Length > 0 && args.Any(a => a.Equals("--benchmark", StringComparison.OrdinalIgnoreCase) || a.Equals("/benchmark", StringComparison.OrdinalIgnoreCase)))
            {
                AttachConsole(ATTACH_PARENT_PROCESS);
                int exitCode = QaRunner.RunBenchmarkAsync(args).GetAwaiter().GetResult();
                Environment.Exit(exitCode);
                return;
            }

            StartupProfiler.Start();
            CrashLogger.Initialize();
            StartupProfiler.Record("Crash logger initialized");

            ServiceLocator.Initialize();
            StartupProfiler.Record("Dependency/service initialization");

            var app = new App();
            app.InitializeComponent();
            StartupProfiler.Record("App & resource loading");

            var mainWindow = new Views.MainWindow();
            StartupProfiler.Record("MainWindow created");

            app.Run(mainWindow);
        }
    }
}
