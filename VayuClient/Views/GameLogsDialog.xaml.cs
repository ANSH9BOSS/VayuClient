using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace VayuClient.Views
{
    public partial class GameLogsDialog : Window
    {
        private readonly string _logFilePath;
        private readonly string _instanceName;
        private CancellationTokenSource? _cts;
        private readonly StringBuilder _rawLogs = new();
        private long _lastReadPosition = 0;

        public GameLogsDialog(string instanceName, string logFilePath)
        {
            InitializeComponent();
            _instanceName = instanceName;
            _logFilePath = logFilePath;
            TxtInstanceBadge.Text = instanceName.ToUpperInvariant();

            Loaded += OnLoaded;
            Closed += OnClosed;
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            _cts = new CancellationTokenSource();
            _ = StartLogTailAsync(_cts.Token);
        }

        private void OnClosed(object? sender, EventArgs e)
        {
            _cts?.Cancel();
            _cts?.Dispose();
        }

        private async Task StartLogTailAsync(CancellationToken token)
        {
            while (!token.IsCancellationRequested)
            {
                try
                {
                    if (File.Exists(_logFilePath))
                    {
                        using var fs = new FileStream(_logFilePath, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                        if (fs.Length < _lastReadPosition)
                        {
                            _lastReadPosition = 0; // File was truncated/recreated
                        }

                        if (fs.Length > _lastReadPosition)
                        {
                            fs.Seek(_lastReadPosition, SeekOrigin.Begin);
                            using var reader = new StreamReader(fs, Encoding.UTF8);
                            string newContent = await reader.ReadToEndAsync(token);
                            _lastReadPosition = fs.Position;

                            if (!string.IsNullOrEmpty(newContent))
                            {
                                Dispatcher.Invoke(() =>
                                {
                                    _rawLogs.Append(newContent);
                                    ApplyFilter();
                                    if (ChkAutoScroll.IsChecked == true)
                                    {
                                        LogScrollViewer.ScrollToEnd();
                                    }
                                });
                            }
                        }
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception)
                {
                    // Ignored during active read
                }

                try
                {
                    await Task.Delay(350, token);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
            }
        }

        private void ApplyFilter()
        {
            string filter = TxtSearchFilter.Text?.Trim() ?? string.Empty;
            if (string.IsNullOrEmpty(filter))
            {
                TxtLogs.Text = _rawLogs.ToString();
            }
            else
            {
                var sb = new StringBuilder();
                using var reader = new StringReader(_rawLogs.ToString());
                string? line;
                while ((line = reader.ReadLine()) != null)
                {
                    if (line.Contains(filter, StringComparison.OrdinalIgnoreCase))
                    {
                        sb.AppendLine(line);
                    }
                }
                TxtLogs.Text = sb.ToString();
            }
        }

        private void TxtSearchFilter_TextChanged(object sender, TextChangedEventArgs e)
        {
            ApplyFilter();
        }

        private void ClearView_Click(object sender, RoutedEventArgs e)
        {
            _rawLogs.Clear();
            TxtLogs.Text = string.Empty;
        }

        private void CopyLogs_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string text = TxtLogs.Text;
                if (!string.IsNullOrEmpty(text))
                {
                    Clipboard.SetText(text);
                }
            }
            catch { }
        }

        private void OpenFolder_Click(object sender, RoutedEventArgs e)
        {
            try
            {
                string dir = Path.GetDirectoryName(_logFilePath) ?? string.Empty;
                if (Directory.Exists(dir))
                {
                    Process.Start(new ProcessStartInfo("explorer.exe", dir) { UseShellExecute = true });
                }
            }
            catch { }
        }

        private void Close_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private void Window_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left)
            {
                DragMove();
            }
        }

        public static void ShowDialogSafe(string instanceName, string logFilePath)
        {
            if (Application.Current != null && Application.Current.Dispatcher != null)
            {
                Application.Current.Dispatcher.InvokeAsync(() =>
                {
                    try
                    {
                        var dialog = new GameLogsDialog(instanceName, logFilePath);
                        if (Application.Current.MainWindow != null && Application.Current.MainWindow.IsVisible)
                        {
                            dialog.Owner = Application.Current.MainWindow;
                        }
                        dialog.Show();
                    }
                    catch { }
                });
            }
        }
    }
}
