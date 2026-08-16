using System;
using System.Diagnostics;

namespace VayuClient.Services.Monitoring
{
    public class PerformanceSnapshot
    {
        public double LauncherCpuPercent { get; set; }
        public double LauncherWorkingSetMB { get; set; }
        public double HostAvailableRamGB { get; set; }
        public double HostTotalRamGB { get; set; }
        public bool IsMinecraftRunning { get; set; }
        public string MinecraftStatus { get; set; } = "Idle / Ready";
        public double MinecraftMemoryMB { get; set; }
        public int MinecraftPid { get; set; }
        public DateTime Timestamp { get; set; } = DateTime.Now;
    }

    public interface IPerformanceMonitorService : IDisposable
    {
        bool IsRunning { get; }
        PerformanceSnapshot CurrentSnapshot { get; }
        event EventHandler<PerformanceSnapshot>? SnapshotUpdated;
        void StartMonitoring(int intervalMs = 1500);
        void StopMonitoring();
        void RegisterMinecraftProcess(Process process);
        void UnregisterMinecraftProcess();
    }
}
