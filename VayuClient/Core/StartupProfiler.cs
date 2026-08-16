using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;

namespace VayuClient.Core
{
    public static class StartupProfiler
    {
        private static readonly Stopwatch _stopwatch = Stopwatch.StartNew();
        private static readonly List<(string Milestone, long ElapsedMs)> _milestones = new();
        private static readonly object _lock = new();
        private static bool _isFlushed = false;

        public static void Start()
        {
            lock (_lock)
            {
                _milestones.Clear();
                _stopwatch.Restart();
                _milestones.Add(("Process started", 0));
            }
        }

        public static void Record(string milestone)
        {
            lock (_lock)
            {
                long elapsed = _stopwatch.ElapsedMilliseconds;
                _milestones.Add((milestone, elapsed));
                Debug.WriteLine($"[STARTUP] {milestone}: {elapsed}ms");
            }
        }

        public static void Flush()
        {
            lock (_lock)
            {
                if (_isFlushed) return;
                _isFlushed = true;

                try
                {
                    var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                    var logDir = Path.Combine(appData, "VayuClient", "Logs");
                    Directory.CreateDirectory(logDir);
                    var logPath = Path.Combine(logDir, "startup.log");

                    using var writer = new StreamWriter(logPath, append: false);
                    writer.WriteLine("==========================================================");
                    writer.WriteLine($" VayuClient Real Startup Profiler Log");
                    writer.WriteLine($" Developer: {AppInfo.DeveloperName}");
                    writer.WriteLine($" Timestamp: {DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}");
                    writer.WriteLine("==========================================================");

                    foreach (var (milestone, elapsed) in _milestones)
                    {
                        writer.WriteLine($"[STARTUP] {milestone}: {elapsed}ms");
                        Console.WriteLine($"[STARTUP] {milestone}: {elapsed}ms");
                    }

                    writer.WriteLine("==========================================================");
                    writer.Flush();
                }
                catch (Exception ex)
                {
                    Debug.WriteLine($"Failed to write startup.log: {ex.Message}");
                }
            }
        }

        public static IReadOnlyList<(string Milestone, long ElapsedMs)> GetMilestones()
        {
            lock (_lock)
            {
                return _milestones.ToArray();
            }
        }
    }
}
