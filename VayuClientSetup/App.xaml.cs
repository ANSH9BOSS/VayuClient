using System;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Threading;

namespace VayuClientSetup
{
    public partial class App : System.Windows.Application
    {
        public static bool IsUninstallMode { get; private set; }
        public static bool IsAutoUpdateMode { get; private set; }

        public App()
        {
            DispatcherUnhandledException += OnDispatcherUnhandledException;
            AppDomain.CurrentDomain.UnhandledException += OnAppDomainUnhandledException;
        }

        protected override void OnStartup(StartupEventArgs e)
        {
            try
            {
                Services.SetupProfiler.Start();
                base.OnStartup(e);
                Services.SetupProfiler.Record("App initialized");

                if (e.Args.Length > 0)
                {
                    if (e.Args.Any(a => a.Equals("/uninstall", StringComparison.OrdinalIgnoreCase) || a.Equals("-uninstall", StringComparison.OrdinalIgnoreCase)))
                    {
                        IsUninstallMode = true;
                    }
                    else if (e.Args.Any(a => 
                        a.Equals("/update", StringComparison.OrdinalIgnoreCase) || 
                        a.Equals("-update", StringComparison.OrdinalIgnoreCase) ||
                        a.Equals("/auto-update", StringComparison.OrdinalIgnoreCase) || 
                        a.Equals("-auto-update", StringComparison.OrdinalIgnoreCase) ||
                        a.Equals("/silent", StringComparison.OrdinalIgnoreCase) || 
                        a.Equals("-silent", StringComparison.OrdinalIgnoreCase)))
                    {
                        IsAutoUpdateMode = true;
                    }
                }

                ShutdownMode = ShutdownMode.OnMainWindowClose;

                var window = new MainWindow();
                MainWindow = window;
                Services.SetupProfiler.Record("MainWindow created");
                window.Show();
                window.Activate();
                window.Focus();
                Services.SetupProfiler.Record("MainWindow shown");
            }
            catch (Exception ex)
            {
                LogException("App.OnStartup", ex);
                System.Windows.MessageBox.Show(
                    $"VayuClient Setup encountered an error while starting up:\n\n{ex.Message}\n\nStack Trace:\n{ex.StackTrace}",
                    "VayuClient Setup Error",
                    MessageBoxButton.OK,
                    MessageBoxImage.Error);
                Shutdown(1);
            }
        }

        private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
        {
            LogException("DispatcherUnhandledException", e.Exception);
            System.Windows.MessageBox.Show(
                $"VayuClient Setup Error:\n\n{e.Exception.Message}\n\n{e.Exception.StackTrace}",
                "VayuClient Setup Error",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            e.Handled = true;
        }

        private void OnAppDomainUnhandledException(object sender, UnhandledExceptionEventArgs e)
        {
            if (e.ExceptionObject is Exception ex)
            {
                LogException("AppDomainUnhandledException", ex);
                System.Windows.MessageBox.Show(
                    $"VayuClient Setup Fatal Error:\n\n{ex.Message}\n\n{ex.StackTrace}",
                    "VayuClient Setup Fatal Error",
                    MessageBoxButton.OK,
                    MessageBoxImage.Error);
            }
        }

        private static void LogException(string source, Exception ex)
        {
            try
            {
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var logDir = Path.Combine(appData, "VayuClient", "Logs");
                Directory.CreateDirectory(logDir);
                var logFile = Path.Combine(logDir, "setup_crash.log");

                var logText = $"[{DateTime.UtcNow:yyyy-MM-dd HH:mm:ss UTC}] [{source}]\n" +
                              $"Exception: {ex.GetType().FullName}: {ex.Message}\n" +
                              $"StackTrace:\n{ex.StackTrace}\n\n";

                File.AppendAllText(logFile, logText);
            }
            catch { }
        }
    }
}
