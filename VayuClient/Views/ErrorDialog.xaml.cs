using System;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Input;

namespace VayuClient.Views
{
    public partial class ErrorDialog : Window
    {
        private readonly string _fullDetails;
        private readonly string _logFilePath;

        public ErrorDialog(string summary, string details, string logFilePath, string? header = null)
        {
            InitializeComponent();
            _fullDetails = details;
            _logFilePath = logFilePath;

            if (!string.IsNullOrEmpty(header))
            {
                TxtHeader.Text = header;
            }

            // Smart Diagnostic Analysis
            string diagnosis = DiagnoseError(summary, details);
            TxtSummary.Text = diagnosis;
            TxtLogLocation.Text = !string.IsNullOrEmpty(logFilePath) ? $"Log file: {logFilePath}" : "Live crash logs captured from game process";
            TxtDetails.Text = details;

            Loaded += (s, e) =>
            {
                try
                {
                    LogScrollViewer.ScrollToEnd();
                }
                catch { }
            };
        }

        private static string DiagnoseError(string summary, string details)
        {
            if (string.IsNullOrWhiteSpace(details)) return summary;

            if (details.Contains("OutOfMemoryError", StringComparison.OrdinalIgnoreCase) ||
                details.Contains("Java heap space", StringComparison.OrdinalIgnoreCase))
            {
                return "Out of Memory: Minecraft exhausted allocated heap memory. Increase RAM in Instance Settings.";
            }

            if (details.Contains("Mod resolution failed", StringComparison.OrdinalIgnoreCase) ||
                details.Contains("Incompatible mods found", StringComparison.OrdinalIgnoreCase) ||
                details.Contains("HARD_DEP", StringComparison.OrdinalIgnoreCase) ||
                details.Contains("breaks", StringComparison.OrdinalIgnoreCase))
            {
                return "Mod Incompatibility: Conflicting or incompatible mod versions detected in instance.";
            }

            if (details.Contains("ClassNotFoundException", StringComparison.OrdinalIgnoreCase) ||
                details.Contains("NoClassDefFoundError", StringComparison.OrdinalIgnoreCase))
            {
                return "Bytecode Error: Missing required class or dependency library for this loader version.";
            }

            if (details.Contains("GLFW error", StringComparison.OrdinalIgnoreCase) ||
                details.Contains("OpenGL", StringComparison.OrdinalIgnoreCase) ||
                details.Contains("WGL", StringComparison.OrdinalIgnoreCase))
            {
                return "Display / GPU Driver Issue: OpenGL or GLFW initialization failed. Update graphics drivers.";
            }

            return summary;
        }

        private void Window_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left)
            {
                try { DragMove(); } catch { }
            }
        }

        private void CopyDetails_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                Clipboard.SetText(_fullDetails);
                TxtCopyStatus.Text = "✓ Full crash log copied to clipboard!";
            }
            catch
            {
                TxtCopyStatus.Text = "Failed to copy to clipboard.";
            }
        }

        private void OpenLog_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                if (File.Exists(_logFilePath))
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = _logFilePath,
                        UseShellExecute = true
                    });
                }
                else
                {
                    // If file doesn't exist on disk yet, dump to temp and open
                    string tempLog = Path.Combine(Path.GetTempPath(), $"VayuCrash_{DateTime.Now:yyyyMMdd_HHmmss}.log");
                    File.WriteAllText(tempLog, _fullDetails);
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = tempLog,
                        UseShellExecute = true
                    });
                }
            }
            catch (Exception ex)
            {
                TxtCopyStatus.Text = $"Could not open log: {ex.Message}";
            }
        }

        private void OpenFolder_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string dir = !string.IsNullOrEmpty(_logFilePath) && File.Exists(_logFilePath)
                    ? Path.GetDirectoryName(_logFilePath)!
                    : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Logs");

                if (!Directory.Exists(dir))
                {
                    Directory.CreateDirectory(dir);
                }

                Process.Start(new ProcessStartInfo
                {
                    FileName = dir,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                TxtCopyStatus.Text = $"Could not open folder: {ex.Message}";
            }
        }

        private void Close_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        public static void ShowDialogSafe(string summary, string details, string logFilePath, string? header = null)
        {
            try
            {
                var app = Application.Current;
                if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess())
                {
                    app.Dispatcher.Invoke(() => ShowDialogSafe(summary, details, logFilePath, header));
                    return;
                }

                var dlg = new ErrorDialog(summary, details, logFilePath, header);
                if (app?.MainWindow != null && app.MainWindow.IsVisible)
                {
                    dlg.Owner = app.MainWindow;
                }
                dlg.ShowDialog();
            }
            catch
            {
                try
                {
                    MessageBox.Show(
                        $"{summary}\n\nLog path: {logFilePath}\n\n{details}",
                        header ?? "VayuClient Error Notice",
                        MessageBoxButton.OK,
                        MessageBoxImage.Warning);
                }
                catch { }
            }
        }
    }
}
