using System;
using System.IO;
using System.Management;
using System.Runtime.InteropServices;
using System.Threading.Tasks;

namespace VayuClient.Services.Hardware
{
    public class HardwareInfoService : IHardwareInfoService
    {
        private HardwareProfile? _cachedProfile;
        private readonly object _lock = new();
        private readonly object _cpuSampleLock = new();
        private bool _hasCpuSample;
        private ulong _previousIdleTime;
        private ulong _previousKernelTime;
        private ulong _previousUserTime;

        [StructLayout(LayoutKind.Sequential)]
        private struct FILETIME
        {
            public uint dwLowDateTime;
            public uint dwHighDateTime;

            public ulong ToUInt64() => ((ulong)dwHighDateTime << 32) | dwLowDateTime;
        }

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
        private class MEMORYSTATUSEX
        {
            public uint dwLength;
            public uint dwMemoryLoad;
            public ulong ullTotalPhys;
            public ulong ullAvailPhys;
            public ulong ullTotalPageFile;
            public ulong ullAvailPageFile;
            public ulong ullTotalVirtual;
            public ulong ullAvailVirtual;
            public ulong ullAvailExtendedVirtual;

            public MEMORYSTATUSEX()
            {
                dwLength = (uint)Marshal.SizeOf(typeof(MEMORYSTATUSEX));
            }
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool GlobalMemoryStatusEx([In, Out] MEMORYSTATUSEX lpBuffer);

        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool GetSystemTimes(out FILETIME idleTime, out FILETIME kernelTime, out FILETIME userTime);

        public HardwareInfoService()
        {
            // Build instant zero-latency baseline profile (< 0.1ms)
            _cachedProfile = CreateFastBaselineProfile();

            // Asynchronously populate full WMI details in background without blocking UI thread
            _ = Task.Run(() =>
            {
                try
                {
                    var enriched = DetectHardwareDetailed();
                    lock (_lock)
                    {
                        _cachedProfile = enriched;
                    }
                }
                catch { }
            });
        }

        public HardwareProfile GetHardwareProfile(bool forceRefresh = false)
        {
            lock (_lock)
            {
                if (_cachedProfile == null)
                {
                    _cachedProfile = CreateFastBaselineProfile();
                }

                // Refresh lightweight dynamic metrics (RAM & Disk)
                RefreshDynamicMetrics(_cachedProfile);
                return _cachedProfile;
            }
        }

        public async Task<HardwareProfile> GetHardwareProfileAsync(bool forceRefresh = false)
        {
            if (!forceRefresh && _cachedProfile != null)
            {
                RefreshDynamicMetrics(_cachedProfile);
                return _cachedProfile;
            }

            return await Task.Run(() =>
            {
                var profile = DetectHardwareDetailed();
                lock (_lock)
                {
                    _cachedProfile = profile;
                }
                return profile;
            });
        }

        public double? GetSystemCpuUsagePercent()
        {
            if (!GetSystemTimes(out var idle, out var kernel, out var user))
            {
                return null;
            }

            var idleNow = idle.ToUInt64();
            var kernelNow = kernel.ToUInt64();
            var userNow = user.ToUInt64();

            lock (_cpuSampleLock)
            {
                if (!_hasCpuSample)
                {
                    _previousIdleTime = idleNow;
                    _previousKernelTime = kernelNow;
                    _previousUserTime = userNow;
                    _hasCpuSample = true;
                    return null;
                }

                var idleDelta = idleNow - _previousIdleTime;
                var totalDelta = (kernelNow - _previousKernelTime) + (userNow - _previousUserTime);
                _previousIdleTime = idleNow;
                _previousKernelTime = kernelNow;
                _previousUserTime = userNow;

                if (totalDelta == 0)
                {
                    return null;
                }

                return Math.Clamp((totalDelta - Math.Min(idleDelta, totalDelta)) * 100.0 / totalDelta, 0.0, 100.0);
            }
        }

        private HardwareProfile CreateFastBaselineProfile()
        {
            var profile = new HardwareProfile
            {
                LogicalProcessors = Math.Max(1, Environment.ProcessorCount),
                PhysicalCores = Math.Max(1, Environment.ProcessorCount / 2),
                OperatingSystemName = $"{Environment.OSVersion.VersionString} ({(Environment.Is64BitOperatingSystem ? "64-bit" : "32-bit")})",
                CpuName = Environment.GetEnvironmentVariable("PROCESSOR_IDENTIFIER") ?? $"{Environment.ProcessorCount}-Core Processor",
                GpuName = "Hardware Acceleration Active"
            };

            RefreshDynamicMetrics(profile);
            ComputeRecommendations(profile);
            return profile;
        }

        private HardwareProfile DetectHardwareDetailed()
        {
            var profile = CreateFastBaselineProfile();

            // 1. CPU Detection via WMI (Background Task)
            try
            {
                using var searcher = new ManagementObjectSearcher("SELECT Name, NumberOfCores, NumberOfLogicalProcessors FROM Win32_Processor");
                foreach (ManagementObject obj in searcher.Get())
                {
                    var name = obj["Name"]?.ToString()?.Trim();
                    if (!string.IsNullOrWhiteSpace(name))
                    {
                        profile.CpuName = name;
                    }

                    if (int.TryParse(obj["NumberOfCores"]?.ToString(), out int cores) && cores > 0)
                    {
                        profile.PhysicalCores = cores;
                    }
                    if (int.TryParse(obj["NumberOfLogicalProcessors"]?.ToString(), out int logical) && logical > 0)
                    {
                        profile.LogicalProcessors = logical;
                    }
                    break;
                }
            }
            catch { }

            // 2. GPU Detection via WMI (Background Task)
            try
            {
                using var searcher = new ManagementObjectSearcher("SELECT Name, AdapterRAM FROM Win32_VideoController");
                long maxVram = 0;
                string primaryGpu = string.Empty;

                foreach (ManagementObject obj in searcher.Get())
                {
                    string gpuName = obj["Name"]?.ToString()?.Trim() ?? string.Empty;
                    long vram = 0;
                    if (obj["AdapterRAM"] != null && long.TryParse(obj["AdapterRAM"]?.ToString(), out long parsedVram))
                    {
                        vram = parsedVram;
                    }

                    if (string.IsNullOrEmpty(primaryGpu))
                    {
                        primaryGpu = gpuName;
                    }

                    bool isDiscrete = gpuName.Contains("NVIDIA", StringComparison.OrdinalIgnoreCase) ||
                                     gpuName.Contains("Radeon", StringComparison.OrdinalIgnoreCase) ||
                                     gpuName.Contains("RTX", StringComparison.OrdinalIgnoreCase) ||
                                     gpuName.Contains("GTX", StringComparison.OrdinalIgnoreCase) ||
                                     gpuName.Contains("Arc", StringComparison.OrdinalIgnoreCase);

                    if (isDiscrete || vram > maxVram)
                    {
                        primaryGpu = gpuName;
                        maxVram = Math.Max(maxVram, vram);
                    }
                }

                if (!string.IsNullOrEmpty(primaryGpu))
                {
                    profile.GpuName = primaryGpu;
                    profile.DedicatedVramBytes = maxVram;
                }
            }
            catch { }

            ComputeRecommendations(profile);
            return profile;
        }

        private static void RefreshDynamicMetrics(HardwareProfile profile)
        {
            try
            {
                var memStatus = new MEMORYSTATUSEX();
                if (GlobalMemoryStatusEx(memStatus))
                {
                    profile.TotalPhysicalRamBytes = (long)memStatus.ullTotalPhys;
                    profile.AvailablePhysicalRamBytes = (long)memStatus.ullAvailPhys;
                }

                string root = Path.GetPathRoot(AppDomain.CurrentDomain.BaseDirectory) ?? "C:\\";
                var drive = new DriveInfo(root);
                if (drive.IsReady)
                {
                    profile.FreeDiskSpaceBytes = drive.AvailableFreeSpace;
                }
            }
            catch { }
        }

        private static void ComputeRecommendations(HardwareProfile profile)
        {
            double totalRamGb = profile.TotalRamGB;

            // Safe memory calculation:
            // 4GB system  -> 2048 MB recommended (max safe: 2560 MB)
            // 8GB system  -> 3584 MB recommended (max safe: 5120 MB)
            // 16GB system -> 6144 MB recommended (max safe: 12288 MB)
            // 32GB+ system -> 8192 MB recommended (max safe: 24576 MB)
            if (totalRamGb <= 4.5)
            {
                profile.RecommendedRamMB = 2048;
                profile.MaxSafeRamMB = 2560;
            }
            else if (totalRamGb <= 8.5)
            {
                profile.RecommendedRamMB = 3584;
                profile.MaxSafeRamMB = 5120;
            }
            else if (totalRamGb <= 16.5)
            {
                profile.RecommendedRamMB = 6144;
                profile.MaxSafeRamMB = 12288;
            }
            else
            {
                profile.RecommendedRamMB = 8192;
                profile.MaxSafeRamMB = (int)(totalRamGb * 1024 - 4096);
            }

            // Concurrency recommendation:
            int logical = profile.LogicalProcessors;
            profile.RecommendedDownloadThreads = Math.Clamp(logical, 4, 12);
            profile.RecommendedGcThreads = Math.Max(2, profile.PhysicalCores);

            profile.SummaryText = $"{profile.CpuName} ({profile.PhysicalCores}C/{profile.LogicalProcessors}T) • {profile.TotalRamGB} GB RAM • {profile.GpuName}";
            profile.RecommendationTip = $"Detected {profile.TotalRamGB} GB System RAM. Recommended Minecraft Allocation: {profile.RecommendedRamMB / 1024.0:0.#} GB ({profile.RecommendedRamMB} MB) leaving {totalRamGb - (profile.RecommendedRamMB / 1024.0):0.#} GB for Windows and apps.";
        }
    }
}
