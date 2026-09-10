using System;
using System.Diagnostics;
using CommunityToolkit.Mvvm.ComponentModel;

namespace VayuClient.Models
{
    /// <summary>
    /// Represents an active running Minecraft game session launched by VayuClient.
    /// Supports multiple concurrent running instances with distinct processes and player profiles.
    /// </summary>
    public partial class RunningGameSession : ObservableObject
    {
        public string SessionId { get; set; } = Guid.NewGuid().ToString("N");
        public string InstanceId { get; set; } = string.Empty;
        public string InstanceName { get; set; } = string.Empty;
        public string PlayerName { get; set; } = string.Empty;
        public int ProcessId { get; set; }
        public DateTime StartTime { get; set; } = DateTime.Now;
        public string LogFilePath { get; set; } = string.Empty;
        public string GameDirectory { get; set; } = string.Empty;

        [ObservableProperty]
        private bool _isRunning = true;

        [ObservableProperty]
        private double _memoryUsageMB;

        public Process? Process { get; set; }

        public string DisplayTitle => $"{InstanceName} • {PlayerName}";

        public string DisplaySubtitle => $"PID: {ProcessId} • Started {StartTime:HH:mm:ss}";

        public string SessionDurationDisplay
        {
            get
            {
                var elapsed = DateTime.Now - StartTime;
                return elapsed.TotalHours >= 1
                    ? $"{(int)elapsed.TotalHours}h {elapsed.Minutes}m"
                    : $"{elapsed.Minutes}m {elapsed.Seconds}s";
            }
        }
    }
}
