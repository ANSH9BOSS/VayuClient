using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Download
{
    public class DownloadService : IDownloadService
    {
        private static readonly HttpClient _httpClient = new(new SocketsHttpHandler
        {
            PooledConnectionLifetime = TimeSpan.FromMinutes(5),
            MaxConnectionsPerServer = 32,
            EnableMultipleHttp2Connections = true
        })
        {
            Timeout = TimeSpan.FromSeconds(30)
        };

        static DownloadService()
        {
            if (!_httpClient.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _httpClient.DefaultRequestHeaders.Add("User-Agent", Core.AppInfo.UserAgent);
            }
        }

        public bool VerifyFileChecksum(string filePath, string? expectedSha1, string? expectedSha256, long expectedSize)
        {
            if (!File.Exists(filePath)) return false;

            var fileInfo = new FileInfo(filePath);
            if (expectedSize > 0 && fileInfo.Length != expectedSize)
            {
                return false;
            }

            if (!string.IsNullOrEmpty(expectedSha1))
            {
                using var fs = File.OpenRead(filePath);
                using var sha1 = SHA1.Create();
                var hashBytes = sha1.ComputeHash(fs);
                var actualSha1 = Convert.ToHexString(hashBytes).ToLowerInvariant();
                return string.Equals(actualSha1, expectedSha1.ToLowerInvariant(), StringComparison.OrdinalIgnoreCase);
            }

            if (!string.IsNullOrEmpty(expectedSha256))
            {
                using var fs = File.OpenRead(filePath);
                using var sha256 = SHA256.Create();
                var hashBytes = sha256.ComputeHash(fs);
                var actualSha256 = Convert.ToHexString(hashBytes).ToLowerInvariant();
                return string.Equals(actualSha256, expectedSha256.ToLowerInvariant(), StringComparison.OrdinalIgnoreCase);
            }

            // If no hash provided, existence + size check is sufficient
            return true;
        }

        public async Task<bool> DownloadFileAsync(DownloadItem item, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default)
        {
            if (string.IsNullOrWhiteSpace(item.Url) || string.IsNullOrWhiteSpace(item.DestinationPath))
            {
                return false;
            }

            // Check if already completely downloaded and valid
            if (VerifyFileChecksum(item.DestinationPath, item.Sha1Hash, item.Sha256Hash, item.ExpectedSize))
            {
                progress?.Report(new DownloadProgressInfo
                {
                    CurrentFileName = Path.GetFileName(item.DestinationPath),
                    CurrentOperation = $"Verified {item.Description}",
                    BytesReceived = item.ExpectedSize,
                    TotalBytes = item.ExpectedSize,
                    SpeedMBPerSec = 0,
                    CompletedFiles = 1,
                    TotalFiles = 1
                });
                return true;
            }

            var parentDir = Path.GetDirectoryName(item.DestinationPath);
            if (!string.IsNullOrEmpty(parentDir))
            {
                Directory.CreateDirectory(parentDir);
            }

            var partFile = item.DestinationPath + ".vayu_part";
            int maxAttempts = 3;

            for (int attempt = 1; attempt <= maxAttempts; attempt++)
            {
                ct.ThrowIfCancellationRequested();

                try
                {
                    if (File.Exists(partFile))
                    {
                        File.Delete(partFile);
                    }

                    using var response = await _httpClient.GetAsync(item.Url, HttpCompletionOption.ResponseHeadersRead, ct);
                    response.EnsureSuccessStatusCode();

                    long totalBytes = response.Content.Headers.ContentLength ?? item.ExpectedSize;
                    long bytesReadTotal = 0;
                    var sw = Stopwatch.StartNew();

                    await using (var contentStream = await response.Content.ReadAsStreamAsync(ct))
                    await using (var fileStream = new FileStream(partFile, FileMode.Create, FileAccess.Write, FileShare.None, 81920, useAsync: true))
                    {
                        var buffer = new byte[81920];
                        int bytesRead;

                        while ((bytesRead = await contentStream.ReadAsync(buffer, 0, buffer.Length, ct)) > 0)
                        {
                            await fileStream.WriteAsync(buffer, 0, bytesRead, ct);
                            bytesReadTotal += bytesRead;

                            if (sw.ElapsedMilliseconds > 100)
                            {
                                double speedMB = (bytesReadTotal / (1024.0 * 1024.0)) / (sw.Elapsed.TotalSeconds > 0 ? sw.Elapsed.TotalSeconds : 0.001);
                                progress?.Report(new DownloadProgressInfo
                                {
                                    CurrentFileName = Path.GetFileName(item.DestinationPath),
                                    CurrentOperation = $"Downloading {item.Description}",
                                    BytesReceived = bytesReadTotal,
                                    TotalBytes = totalBytes > 0 ? totalBytes : bytesReadTotal,
                                    SpeedMBPerSec = speedMB,
                                    CompletedFiles = 0,
                                    TotalFiles = 1
                                });
                            }
                        }
                    }

                    // Verify checksum of part file
                    if (VerifyFileChecksum(partFile, item.Sha1Hash, item.Sha256Hash, item.ExpectedSize))
                    {
                        if (File.Exists(item.DestinationPath))
                        {
                            File.Delete(item.DestinationPath);
                        }
                        File.Move(partFile, item.DestinationPath, overwrite: true);

                        progress?.Report(new DownloadProgressInfo
                        {
                            CurrentFileName = Path.GetFileName(item.DestinationPath),
                            CurrentOperation = $"Installed {item.Description}",
                            BytesReceived = bytesReadTotal,
                            TotalBytes = bytesReadTotal,
                            SpeedMBPerSec = 0,
                            CompletedFiles = 1,
                            TotalFiles = 1
                        });

                        return true;
                    }
                    else
                    {
                        if (File.Exists(partFile)) File.Delete(partFile);
                        if (attempt == maxAttempts)
                        {
                            return false;
                        }
                        await Task.Delay(500 * attempt, ct);
                    }
                }
                catch (Exception) when (attempt < maxAttempts && !ct.IsCancellationRequested)
                {
                    if (File.Exists(partFile)) { try { File.Delete(partFile); } catch { } }
                    await Task.Delay(500 * attempt, ct);
                }
                catch (Exception)
                {
                    if (File.Exists(partFile)) { try { File.Delete(partFile); } catch { } }
                    return false;
                }
            }

            return false;
        }

        public async Task<BatchDownloadResult> DownloadBatchAsync(
            IEnumerable<DownloadItem> items,
            int maxConcurrency = 12,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default)
        {
            var itemList = items.ToList();
            var result = new BatchDownloadResult
            {
                TotalItems = itemList.Count
            };

            if (itemList.Count == 0)
            {
                result.Success = true;
                return result;
            }

            // Identify existing valid files vs items to download
            var toDownload = new List<DownloadItem>();
            long totalBatchBytes = 0;
            long alreadyVerifiedBytes = 0;

            foreach (var item in itemList)
            {
                totalBatchBytes += Math.Max(item.ExpectedSize, 0);
                if (VerifyFileChecksum(item.DestinationPath, item.Sha1Hash, item.Sha256Hash, item.ExpectedSize))
                {
                    result.SkippedItems++;
                    alreadyVerifiedBytes += Math.Max(item.ExpectedSize, 0);
                }
                else
                {
                    toDownload.Add(item);
                }
            }

            if (toDownload.Count == 0)
            {
                result.Success = true;
                progress?.Report(new DownloadProgressInfo
                {
                    CurrentFileName = "All files verified",
                    CurrentOperation = "Up to date",
                    BytesReceived = totalBatchBytes,
                    TotalBytes = totalBatchBytes,
                    CompletedFiles = itemList.Count,
                    TotalFiles = itemList.Count,
                    SpeedMBPerSec = 0
                });
                return result;
            }

            long totalBytesDownloaded = alreadyVerifiedBytes;
            int completedFilesCount = result.SkippedItems;
            int downloadedItemsCount = 0;
            int failedItemsCount = 0;
            var semaphore = new SemaphoreSlim(Math.Max(1, maxConcurrency));
            var sw = Stopwatch.StartNew();
            long sessionBytesTransferred = 0;

            var tasks = toDownload.Select(async item =>
            {
                await semaphore.WaitAsync(ct);
                try
                {
                    bool ok = await DownloadFileAsync(item, null, ct);
                    if (ok)
                    {
                        Interlocked.Increment(ref downloadedItemsCount);
                        Interlocked.Add(ref totalBytesDownloaded, Math.Max(item.ExpectedSize, 0));
                        Interlocked.Add(ref sessionBytesTransferred, Math.Max(item.ExpectedSize, 0));
                    }
                    else
                    {
                        Interlocked.Increment(ref failedItemsCount);
                        lock (result.Errors)
                        {
                            result.Errors.Add($"Failed to download or verify checksum: {item.Url}");
                        }
                    }

                    int curCompleted = Interlocked.Increment(ref completedFilesCount);
                    double speedMB = (Interlocked.Read(ref sessionBytesTransferred) / (1024.0 * 1024.0)) / (sw.Elapsed.TotalSeconds > 0 ? sw.Elapsed.TotalSeconds : 0.001);

                    progress?.Report(new DownloadProgressInfo
                    {
                        CurrentFileName = Path.GetFileName(item.DestinationPath),
                        CurrentOperation = $"Downloading ({curCompleted}/{itemList.Count})",
                        BytesReceived = Interlocked.Read(ref totalBytesDownloaded),
                        TotalBytes = totalBatchBytes,
                        CompletedFiles = curCompleted,
                        TotalFiles = itemList.Count,
                        SpeedMBPerSec = speedMB
                    });
                }
                finally
                {
                    semaphore.Release();
                }
            });

            await Task.WhenAll(tasks);

            result.DownloadedItems = downloadedItemsCount;
            result.FailedItems = failedItemsCount;
            result.Success = (result.FailedItems == 0);
            return result;
        }
    }
}
