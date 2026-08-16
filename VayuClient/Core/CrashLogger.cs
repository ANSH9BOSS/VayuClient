using System;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows;

namespace VayuClient.Core
{
    public static class CrashLogger
    {
        private static readonly object _lock = new();
        private static string? _launcherLogPath;
        private static string? _crashLogPath;

        public static string CurrentPage { get; set; } = "Home";

        public static void Initialize()
        {
            try
            {
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var logDir = Path.Combine(appData, "VayuClient", "logs");
                Directory.CreateDirectory(logDir);
                _launcherLogPath = Path.Combine(logDir, "launcher.log");
                _crashLogPath = Path.Combine(logDir, "crash.log");

                AppDomain.CurrentDomain.UnhandledException += (s, e) =>
                {
                    if (e.ExceptionObject is Exception ex)
                    {
                        LogException("AppDomain.UnhandledException", ex);
                    }
                };

                TaskScheduler.UnobservedTaskException += (s, e) =>
                {
                    LogException("TaskScheduler.UnobservedTaskException", e.Exception);
                    e.SetObserved();
                };

                if (Application.Current != null)
                {
                    Application.Current.DispatcherUnhandledException += (s, e) =>
                    {
                        LogException("DispatcherUnhandledException", e.Exception);
                        e.Handled = true; // Keep application alive

                        try
                        {
                            var summary = $"An unexpected error occurred on page '{CurrentPage}': {e.Exception.Message}";
                            var details = $"Page: {CurrentPage}\nException: {e.Exception.GetType().FullName}\nMessage: {e.Exception.Message}\n\nStack Trace:\n{e.Exception.StackTrace}";
                            Views.ErrorDialog.ShowDialogSafe(summary, Sanitize(details), _crashLogPath ?? "crash.log");
                        }
                        catch { }
                    };
                }

                LogMessage("==========================================================");
                LogMessage($"VayuClient Launcher Session Started at {DateTime.Now:yyyy-MM-dd HH:mm:ss}");
                LogMessage("==========================================================");
            }
            catch { }
        }

        public static void LogException(string source, Exception ex, string? pageContext = null)
        {
            var page = pageContext ?? CurrentPage;
            var sanitizedMsg = Sanitize(ex.Message);
            var sanitizedStack = Sanitize(ex.StackTrace ?? string.Empty);
            var innerMsg = ex.InnerException != null ? Sanitize(ex.InnerException.Message) : null;
            var innerStack = ex.InnerException != null ? Sanitize(ex.InnerException.StackTrace ?? string.Empty) : null;

            var logEntry = $@"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] [ERROR] [{source}] [Page: {page}]
Exception: {ex.GetType().FullName}
Message: {sanitizedMsg}
Stack Trace:
{sanitizedStack}
" + (innerMsg != null ? $@"Inner Exception: {ex.InnerException!.GetType().FullName}
Inner Message: {innerMsg}
Inner Stack Trace:
{innerStack}
" : "") + "----------------------------------------------------------";

            LogMessage(logEntry);

            // Also append specifically to crash.log
            try
            {
                lock (_lock)
                {
                    if (_crashLogPath != null)
                    {
                        File.AppendAllText(_crashLogPath, logEntry + Environment.NewLine);
                    }
                }
            }
            catch { }
        }

        private static readonly System.Collections.Concurrent.ConcurrentQueue<string> _liveLogQueue = new();
        public static event Action? LogsUpdated;

        public static string GetLiveLogsText()
        {
            lock (_lock)
            {
                if (_liveLogQueue.IsEmpty)
                {
                    if (_launcherLogPath != null && File.Exists(_launcherLogPath))
                    {
                        try { return File.ReadAllText(_launcherLogPath); } catch { }
                    }
                    return "No launcher logs available.";
                }
                return string.Join(Environment.NewLine, _liveLogQueue);
            }
        }

        public static void Clear()
        {
            lock (_lock)
            {
                while (_liveLogQueue.TryDequeue(out _)) { }
                try
                {
                    if (_launcherLogPath != null && File.Exists(_launcherLogPath))
                    {
                        File.WriteAllText(_launcherLogPath, string.Empty);
                    }
                }
                catch { }
            }
            LogsUpdated?.Invoke();
        }

        public static void LogMessage(string message)
        {
            try
            {
                var entry = $"[{DateTime.Now:HH:mm:ss} INFO] {message}";
                _liveLogQueue.Enqueue(entry);
                while (_liveLogQueue.Count > 500) _liveLogQueue.TryDequeue(out _);

                lock (_lock)
                {
                    if (_launcherLogPath == null)
                    {
                        var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                        var logDir = Path.Combine(appData, "VayuClient", "logs");
                        Directory.CreateDirectory(logDir);
                        _launcherLogPath = Path.Combine(logDir, "launcher.log");
                        _crashLogPath = Path.Combine(logDir, "crash.log");
                    }

                    File.AppendAllText(_launcherLogPath, entry + Environment.NewLine);
                }

                LogsUpdated?.Invoke();
            }
            catch { }
        }

        public static string GetRecentLogLines(int count = 60)
        {
            lock (_lock)
            {
                var items = _liveLogQueue.ToArray();
                if (items.Length == 0) return GetLiveLogsText();
                int skip = Math.Max(0, items.Length - count);
                return string.Join(Environment.NewLine, items.Skip(skip));
            }
        }

        public static (string details, string logPath) GetCrashDetails(string? instanceDir, string fallbackMessage)
        {
            try
            {
                if (!string.IsNullOrEmpty(instanceDir) && Directory.Exists(instanceDir))
                {
                    // 1. Check crash-reports directory for latest crash report
                    var crashReportsDir = Path.Combine(instanceDir, "crash-reports");
                    if (Directory.Exists(crashReportsDir))
                    {
                        var latestReport = new DirectoryInfo(crashReportsDir)
                            .GetFiles("*.txt")
                            .OrderByDescending(f => f.LastWriteTimeUtc)
                            .FirstOrDefault();

                        if (latestReport != null && (DateTime.UtcNow - latestReport.LastWriteTimeUtc).TotalMinutes < 10)
                        {
                            return (File.ReadAllText(latestReport.FullName), latestReport.FullName);
                        }
                    }

                    // 2. Check logs/latest.log
                    var latestLog = Path.Combine(instanceDir, "logs", "latest.log");
                    if (File.Exists(latestLog))
                    {
                        var lines = File.ReadAllLines(latestLog);
                        int take = Math.Min(100, lines.Length);
                        string text = string.Join(Environment.NewLine, lines.Skip(lines.Length - take));
                        return (text, latestLog);
                    }
                }
            }
            catch { }

            string fallbackText = !string.IsNullOrEmpty(fallbackMessage) 
                ? $"{fallbackMessage}\n\nRecent Process Output:\n{GetRecentLogLines(60)}"
                : GetRecentLogLines(60);

            return (fallbackText, _launcherLogPath ?? "launcher.log");
        }

        public static string Sanitize(string input)
        {
            if (string.IsNullOrEmpty(input)) return string.Empty;

            // Mask access tokens, refresh tokens, passwords
            var sanitized = Regex.Replace(input, @"(access_token|accessToken|refresh_token|refreshToken|password|client_secret)=([^&\s""']+)", "$1=[PROTECTED_TOKEN]", RegexOptions.IgnoreCase);
            sanitized = Regex.Replace(sanitized, @"Bearer\s+[a-zA-Z0-9\-_.]+", "Bearer [PROTECTED_TOKEN]", RegexOptions.IgnoreCase);
            return sanitized;
        }
    }
}
