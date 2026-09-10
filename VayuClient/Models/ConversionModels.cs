using System;
using System.Collections.Generic;

namespace VayuClient.Models
{
    public enum ConversionPhase
    {
        Scanning,
        ResolvingModrinth,
        Downloading,
        CopyingAssets,
        Finalizing,
        Completed,
        Failed
    }

    public class ConversionOptions
    {
        public string TargetMinecraftVersion { get; set; } = string.Empty;
        public string TargetLoader { get; set; } = "Fabric";
        public string NewInstanceName { get; set; } = string.Empty;
        public bool CopyResourcePacks { get; set; } = true;
        public bool CopyShaderPacks { get; set; } = true;
        public bool CopyOptions { get; set; } = true;
        public int RamMB { get; set; } = 4096;
    }

    public class ConversionProgressInfo
    {
        public string CurrentOperation { get; set; } = string.Empty;
        public string CurrentItem { get; set; } = string.Empty;
        public int ProcessedCount { get; set; }
        public int TotalCount { get; set; }
        public double Percent { get; set; }
        public ConversionPhase Phase { get; set; }
    }

    public class PortedModInfo
    {
        public string ModName { get; set; } = string.Empty;
        public string ModId { get; set; } = string.Empty;
        public string OriginalFileName { get; set; } = string.Empty;
        public string NewFileName { get; set; } = string.Empty;
        public string TargetVersion { get; set; } = string.Empty;
        public string DownloadUrl { get; set; } = string.Empty;
        public long FileSize { get; set; }
        public string FormattedSize => FileSize > 1024 * 1024 
            ? $"{(FileSize / (1024.0 * 1024.0)):F1} MB" 
            : $"{(FileSize / 1024.0):F0} KB";
    }

    public class MissingModInfo
    {
        public string ModName { get; set; } = string.Empty;
        public string ModId { get; set; } = string.Empty;
        public string OriginalFileName { get; set; } = string.Empty;
        public string Reason { get; set; } = string.Empty;
        public string ModrinthSearchUrl { get; set; } = string.Empty;
    }

    public class ConversionResult
    {
        public bool Success { get; set; }
        public MinecraftInstance? SourceInstance { get; set; }
        public MinecraftInstance? ConvertedInstance { get; set; }
        public List<PortedModInfo> PortedMods { get; set; } = new();
        public List<MissingModInfo> MissingMods { get; set; } = new();
        public List<string> CopiedResourcePacks { get; set; } = new();
        public List<string> CopiedShaderPacks { get; set; } = new();
        public string? ErrorMessage { get; set; }

        public int TotalSourceMods => PortedMods.Count + MissingMods.Count;
        public double PortSuccessRate => TotalSourceMods > 0 ? (double)PortedMods.Count / TotalSourceMods * 100.0 : 100.0;
    }
}
