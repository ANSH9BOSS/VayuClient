using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;
using VayuClient.Services.Download;

namespace VayuClient.Services.Integrity
{
    public class InstanceIntegrityReport
    {
        public bool IsValid { get; set; } = true;
        public string RequestedMinecraftVersion { get; set; } = string.Empty;
        public string? DetectedMinecraftVersion { get; set; }
        public string RequestedLoader { get; set; } = "Vanilla";
        public string? DetectedLoader { get; set; }
        public string? LoaderVersion { get; set; }
        public bool ClientJarExists { get; set; }
        public bool VersionJsonValid { get; set; }
        public bool LoaderCompatible { get; set; } = true;
        public bool JavaCompatible { get; set; } = true;
        public List<string> Errors { get; } = new();
        public List<string> Warnings { get; } = new();

        public string GetSummary()
        {
            if (IsValid) return "Integrity Check: PASS (All artifacts, metadata, and runtime dependencies verified)";
            return $"Integrity Check: FAIL ({string.Join("; ", Errors)})";
        }
    }

    public interface IInstanceIntegrityService
    {
        Task<InstanceIntegrityReport> ValidateIntegrityAsync(MinecraftInstance instance, CancellationToken ct = default);
        Task<bool> RepairInstanceAsync(MinecraftInstance instance, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default);
    }
}
