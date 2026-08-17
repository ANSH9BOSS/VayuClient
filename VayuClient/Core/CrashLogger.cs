using System;
using System.Collections.Concurrent;
using System.IO;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;

namespace VayuClient.Core
{
    public static class CrashLogger
    {
        private static readonly object _lock = new();
        private static string? _launcherLogPath;
        private static string? _crashLogPath;

        public static string CurrentPage { get; set; } = "Home";

        private static readonly ConcurrentQueue<string> _liveLogQueue = new();
        private static readonly ConcurrentQueue<string> _diskWriteQueue = new();
        private static readonly System.Timers.Timer _flushTimer = new(500);
        private static bool _pendingUiNotification;

        public static event Action? LogsUpdated;

        public static void Initialize()
        {
            try
            {
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var logDir = Path.Combine(appData, "VayuClient", "logs");
                Directory.CreateDirectory(logDir);
                _launcherLogPath = Path.Combine(logDir, "launcher.log");
                _crashLogPath = Path.Combine(logDir, "crash.log");

                _flushTimer.Elapsed += (s, e) => FlushQueuedLogsToDisk();
                _flushTimer.AutoReset = true;
                _flushTimer.Start();

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

        private static void FlushQueuedLogsToDisk()
        {
            if (_diskWriteQueue.IsEmpty) return;

            var sb = new System.Text.StringBuilder();
            while (_diskWriteQueue.TryDequeue(out var line))
            {
                sb.AppendLine(line);
            }

            var text = sb.ToString();
            if (!string.IsNullOrEmpty(text) && _launcherLogPath != null)
            {
                try
                {
                    File.AppendAllText(_launcherLogPath, text);
                }
                catch { }
            }

            if (_pendingUiNotification)
            {
                _pendingUiNotification = false;
                try
                {
                    LogsUpdated?.Invoke();
                }
                catch { }
            }
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

            // Also append immediately to crash.log
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

        public static string GetLiveLogsText()
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

        public static void Clear()
        {
            while (_liveLogQueue.TryDequeue(out _)) { }
            while (_diskWriteQueue.TryDequeue(out _)) { }
            try
            {
                if (_launcherLogPath != null && File.Exists(_launcherLogPath))
                {
                    File.WriteAllText(_launcherLogPath, string.Empty);
                }
            }
            catch { }
            LogsUpdated?.Invoke();
        }

        public static void LogMessage(string message)
        {
            try
            {
                var entry = $"[{DateTime.Now:HH:mm:ss} INFO] {message}";
                _liveLogQueue.Enqueue(entry);
                while (_liveLogQueue.Count > 400) _liveLogQueue.TryDequeue(out _);

                _diskWriteQueue.Enqueue(entry);
                _pendingUiNotification = true;
            }
            catch { }
        }

        public static (string Details, string LogFilePath) GetCrashDetails(string? gameDir, string fallbackError)
        {
            try
            {
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var launcherLog = Path.Combine(appData, "VayuClient", "logs", "launcher.log");

                if (!string.IsNullOrEmpty(gameDir) && Directory.Exists(gameDir))
                {
                    var crashReportsDir = Path.Combine(gameDir, "crash-reports");
                    if (Directory.Exists(crashReportsDir))
                    {
                        var latestCrash = new DirectoryInfo(crashReportsDir)
                            .GetFiles("crash-*.txt")
                            .OrderByDescending(f => f.LastWriteTimeUtc)
                            .FirstOrDefault();

                        if (latestCrash != null && (DateTime.UtcNow - latestCrash.LastWriteTimeUtc).TotalMinutes < 5)
                        {
                            return (Sanitize(File.ReadAllText(latestCrash.FullName)), latestCrash.FullName);
                        }
                    }

                    var latestLog = Path.Combine(gameDir, "logs", "latest.log");
                    if (File.Exists(latestLog))
                    {
                        var lines = File.ReadAllLines(latestLog);
                        var tail = string.Join(Environment.NewLine, lines.TakeLast(60));
                        return (Sanitize(tail), latestLog);
                    }
                }

                if (File.Exists(launcherLog))
                {
                    var lines = File.ReadAllLines(launcherLog);
                    var tail = string.Join(Environment.NewLine, lines.TakeLast(40));
                    return (Sanitize(tail), launcherLog);
                }
            }
            catch { }

            return (Sanitize(fallbackError), "None");
        }

        public static string Sanitize(string input)
        {
            if (string.IsNullOrEmpty(input)) return string.Empty;

            // Remove access tokens, session tokens, passwords, and emails
            input = Regex.Replace(input, @"(accessToken|token|session|auth_session|password)=([^\s&""']+)", "$1=REDACTED", RegexOptions.IgnoreCase);
            input = Regex.Replace(input, @"Bearer\s+[A-Za-z0-9\-\._~\+\/]+=*", "Bearer REDACTED", RegexOptions.IgnoreCase);
            input = Regex.Replace(input, @"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}", "[EMAIL_REDACTED]");

            return input;
        }
    }
}
