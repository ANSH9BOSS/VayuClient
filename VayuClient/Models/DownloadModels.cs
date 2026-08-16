using System;

namespace VayuClient.Models
{
    public class DownloadItem
    {
        public string Url { get; set; } = string.Empty;
        public string DestinationPath { get; set; } = string.Empty;
        public string? Sha1Hash { get; set; }
        public string? Sha256Hash { get; set; }
        public long ExpectedSize { get; set; }
        public string Category { get; set; } = "General"; // Client, Library, Asset, Mod, Modpack
        public string Description { get; set; } = string.Empty;
    }

    public class DownloadProgressInfo
    {
        public string CurrentFileName { get; set; } = string.Empty;
        public string CurrentOperation { get; set; } = string.Empty;
        public long BytesReceived { get; set; }
        public long TotalBytes { get; set; }
        public double Percentage => TotalBytes > 0 ? Math.Min(100.0, Math.Max(0.0, (double)BytesReceived / TotalBytes * 100.0)) : 0.0;
        public double SpeedMBPerSec { get; set; }
        public int CompletedFiles { get; set; }
        public int TotalFiles { get; set; }

        public string SpeedDisplay => SpeedMBPerSec >= 1.0 
            ? $"{SpeedMBPerSec:0.0} MB/s" 
            : $"{SpeedMBPerSec * 1024.0:0} KB/s";

        public string ProgressDisplay => TotalBytes > 0 
            ? $"{BytesReceived / 1048576.0:0.0} MB / {TotalBytes / 1048576.0:0.0} MB ({Percentage:0}%)" 
            : $"{BytesReceived / 1048576.0:0.0} MB";
    }

    public class BatchDownloadResult
    {
        public bool Success { get; set; }
        public int TotalItems { get; set; }
        public int DownloadedItems { get; set; }
        public int SkippedItems { get; set; }
        public int FailedItems { get; set; }
        public List<string> Errors { get; set; } = new();
    }
}
