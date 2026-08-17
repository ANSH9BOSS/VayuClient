using System;
using System.Diagnostics;
using System.Threading;
using VayuClient.Services.Hardware;

namespace VayuClient.Services.Monitoring
{
    public class PerformanceMonitorService : IPerformanceMonitorService
    {
        private readonly IHardwareInfoService _hardwareInfoService;
        private Timer? _timer;
        private readonly object _lock = new();
        private bool _isRunning;
        private Process? _minecraftProcess;
        
        private TimeSpan _lastCpuTime;
        private DateTime _lastSampleTime;
        private readonly Process _currentProcess;
        private readonly int _processorCount;
        private HardwareProfile? _cachedHw;
        private DateTime _lastHwRefresh = DateTime.MinValue;

        public bool IsRunning => _isRunning;
        public PerformanceSnapshot CurrentSnapshot { get; private set; } = new();
        public event EventHandler<PerformanceSnapshot>? SnapshotUpdated;

        public PerformanceMonitorService(IHardwareInfoService hardwareInfoService)
        {
            _hardwareInfoService = hardwareInfoService ?? throw new ArgumentNullException(nameof(hardwareInfoService));
            _currentProcess = Process.GetCurrentProcess();
            _processorCount = Math.Max(1, Environment.ProcessorCount);
            _lastCpuTime = _currentProcess.TotalProcessorTime;
            _lastSampleTime = DateTime.UtcNow;
        }

        public void StartMonitoring(int intervalMs = 2500)
        {
            lock (_lock)
            {
                if (_isRunning) return;
                _isRunning = true;
                _lastCpuTime = _currentProcess.TotalProcessorTime;
                _lastSampleTime = DateTime.UtcNow;
                _timer = new Timer(OnTimerTick, null, 0, intervalMs);
            }
        }

        public void StopMonitoring()
        {
            lock (_lock)
            {
                if (!_isRunning) return;
                _isRunning = false;
                _timer?.Dispose();
                _timer = null;
            }
        }

        public void RegisterMinecraftProcess(Process process)
        {
            lock (_lock)
            {
                _minecraftProcess = process;
            }
        }

        public void UnregisterMinecraftProcess()
        {
            lock (_lock)
            {
                _minecraftProcess = null;
            }
        }

        private void OnTimerTick(object? state)
        {
            try
            {
                var now = DateTime.UtcNow;
                var timeDelta = now - _lastSampleTime;
                if (timeDelta.TotalMilliseconds < 500) return;

                _currentProcess.Refresh();
                var currentCpuTime = _currentProcess.TotalProcessorTime;
                var cpuUsedMs = (currentCpuTime - _lastCpuTime).TotalMilliseconds;
                var totalAvailableMs = timeDelta.TotalMilliseconds * _processorCount;

                double cpuPercent = Math.Clamp((cpuUsedMs / totalAvailableMs) * 100.0, 0.0, 100.0);
                _lastCpuTime = currentCpuTime;
                _lastSampleTime = now;

                // Cache hardware profile so we never query WMI continuously during gameplay
                if (_cachedHw == null || (now - _lastHwRefresh).TotalSeconds > 60)
                {
                    _cachedHw = _hardwareInfoService.GetHardwareProfile(forceRefresh: false);
                    _lastHwRefresh = now;
                }

                var snapshot = new PerformanceSnapshot
                {
                    LauncherCpuPercent = Math.Round(cpuPercent, 1),
                    LauncherWorkingSetMB = Math.Round(_currentProcess.WorkingSet64 / (1024.0 * 1024.0), 1),
                    HostAvailableRamGB = _cachedHw.AvailableRamGB,
                    HostTotalRamGB = _cachedHw.TotalRamGB,
                    Timestamp = DateTime.Now
                };

                lock (_lock)
                {
                    if (_minecraftProcess != null)
                    {
                        try
                        {
                            if (!_minecraftProcess.HasExited)
                            {
                                snapshot.IsMinecraftRunning = true;
                                snapshot.MinecraftPid = _minecraftProcess.Id;
                                snapshot.MinecraftMemoryMB = Math.Round(_minecraftProcess.WorkingSet64 / (1024.0 * 1024.0), 1);
                                snapshot.MinecraftStatus = $"Running (PID {_minecraftProcess.Id}) • {snapshot.MinecraftMemoryMB:0} MB RAM";
                            }
                            else
                            {
                                _minecraftProcess = null;
                                snapshot.IsMinecraftRunning = false;
                                snapshot.MinecraftStatus = "Terminated";
                            }
                        }
                        catch
                        {
                            _minecraftProcess = null;
                            snapshot.IsMinecraftRunning = false;
                            snapshot.MinecraftStatus = "Idle / Ready";
                        }
                    }
                    else
                    {
                        snapshot.IsMinecraftRunning = false;
                        snapshot.MinecraftStatus = "Idle / Ready";
                    }
                }

                CurrentSnapshot = snapshot;
                SnapshotUpdated?.Invoke(this, snapshot);
            }
            catch
            {
                // Silently handle monitoring errors
            }
        }

        public void Dispose()
        {
            StopMonitoring();
        }
    }
}
