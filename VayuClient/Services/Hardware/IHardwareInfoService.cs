using System;
using System.Threading.Tasks;

namespace VayuClient.Services.Hardware
{
    public class HardwareProfile
    {
        public string CpuName { get; set; } = "Unknown CPU";
        public int PhysicalCores { get; set; } = 4;
        public int LogicalProcessors { get; set; } = Environment.ProcessorCount;
        public string GpuName { get; set; } = "DirectX Graphics Adapter";
        public long DedicatedVramBytes { get; set; }
        public long TotalPhysicalRamBytes { get; set; }
        public long AvailablePhysicalRamBytes { get; set; }
        public string OperatingSystemName { get; set; } = Environment.OSVersion.ToString();
        public long FreeDiskSpaceBytes { get; set; }
        
        public double TotalRamGB => Math.Round(TotalPhysicalRamBytes / (1024.0 * 1024.0 * 1024.0), 1);
        public double AvailableRamGB => Math.Round(AvailablePhysicalRamBytes / (1024.0 * 1024.0 * 1024.0), 1);
        public double DedicatedVramGB => Math.Round(DedicatedVramBytes / (1024.0 * 1024.0 * 1024.0), 1);
        public double FreeDiskGB => Math.Round(FreeDiskSpaceBytes / (1024.0 * 1024.0 * 1024.0), 1);

        public int RecommendedRamMB { get; set; } = 4096;
        public int MaxSafeRamMB { get; set; } = 8192;
        public int RecommendedDownloadThreads { get; set; } = 6;
        public int RecommendedGcThreads { get; set; } = 4;
        
        public string SummaryText { get; set; } = string.Empty;
        public string RecommendationTip { get; set; } = string.Empty;
    }

    public interface IHardwareInfoService
    {
        HardwareProfile GetHardwareProfile(bool forceRefresh = false);
        Task<HardwareProfile> GetHardwareProfileAsync(bool forceRefresh = false);
    }
}
