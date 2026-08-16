using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Performance
{
    public class PerformanceSettings
    {
        public bool EnableFpsBooster { get; set; } = true;
        public bool EnableDynamicFps { get; set; } = true;
        public int UnfocusedFpsLimit { get; set; } = 30;
        public bool EnableEntityCulling { get; set; } = true;
        public double MobRenderDistanceScale { get; set; } = 1.0; // 0.5 to 2.0
        public int ParticleQuality { get; set; } = 0; // 0 = All, 1 = Decreased, 2 = Minimal
        public bool LimitAnimations { get; set; } = false;
        public int RenderDistanceChunks { get; set; } = 12;
        public int SimulationDistanceChunks { get; set; } = 8;
        public bool EnableVsync { get; set; } = false;
    }

    public class OptimizationRecommendation
    {
        public string Title { get; set; } = string.Empty;
        public string Description { get; set; } = string.Empty;
        public int RecommendedRamMB { get; set; }
        public int RecommendedRenderDistance { get; set; }
        public int RecommendedSimulationDistance { get; set; }
        public List<string> RecommendedJvmFlags { get; } = new();
        public List<string> AppliedOptimizations { get; } = new();
    }

    public class ResourceMetricsSnapshot
    {
        public double CpuUsagePercent { get; set; }
        public double RamUsagePercent { get; set; }
        public long WorkingSetMB { get; set; }
        public double AvailableRamGB { get; set; }
        public double TotalRamGB { get; set; }
        public DateTime Timestamp { get; set; } = DateTime.UtcNow;
    }

    public interface IPerformanceService
    {
        PerformanceSettings CurrentSettings { get; }
        void UpdateSettings(PerformanceSettings settings);
        string GenerateOptimizedJvmFlags(MinecraftInstance instance, int ramMB);
        Task ApplyInstanceOptionsAsync(MinecraftInstance instance, PerformanceSettings settings);
        OptimizationRecommendation GetAiOptimizationRecommendation(MinecraftInstance instance);
        ResourceMetricsSnapshot GetCurrentResourceMetrics();
    }
}
