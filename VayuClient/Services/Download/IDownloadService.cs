using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Download
{
    public interface IDownloadService
    {
        Task<bool> DownloadFileAsync(DownloadItem item, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default);
        Task<BatchDownloadResult> DownloadBatchAsync(IEnumerable<DownloadItem> items, int maxConcurrency = 12, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default);
        bool VerifyFileChecksum(string filePath, string? expectedSha1, string? expectedSha256, long expectedSize);
    }
}
