using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Download;
using VayuClient.Services.Instance;

namespace VayuClient.Services.Modpack
{
    public class ModpackInstaller : IModpackInstaller
    {
        private readonly IDownloadService _downloadService;
        private static readonly HttpClient _http = new()
        {
            Timeout = TimeSpan.FromSeconds(25)
        };

        static ModpackInstaller()
        {
            if (!_http.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _http.DefaultRequestHeaders.Add("User-Agent", Core.AppInfo.UserAgent);
            }
        }

        public ModpackInstaller(IDownloadService downloadService)
        {
            _downloadService = downloadService;
        }

        public async Task<bool> InstallModpackAsync(
            MinecraftInstance instance,
            string modpackId,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default)
        {
            if (string.IsNullOrEmpty(modpackId) || modpackId.Equals("none", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            progress?.Report(new DownloadProgressInfo
            {
                CurrentFileName = modpackId,
                CurrentOperation = "Resolving Modrinth modpack version...",
                CompletedFiles = 0,
                TotalFiles = 1
            });

            // 1. Fetch Modrinth Project Versions
            string versionsUrl = $"https://api.modrinth.com/v2/project/{Uri.EscapeDataString(modpackId)}/version";
            var res = await _http.GetAsync(versionsUrl, ct);
            if (!res.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"Failed to query Modrinth modpack versions: {res.StatusCode}");
            }

            var json = await res.Content.ReadAsStringAsync(ct);
            var versionArray = JArray.Parse(json);

            JToken? selectedVersion = null;
            foreach (var ver in versionArray)
            {
                var gameVersions = ver["game_versions"]?.Select(v => v.ToString()) ?? Enumerable.Empty<string>();
                var loaders = ver["loaders"]?.Select(l => l.ToString().ToLowerInvariant()) ?? Enumerable.Empty<string>();

                bool verMatch = gameVersions.Contains(instance.MinecraftVersion) || !gameVersions.Any();
                bool loaderMatch = string.IsNullOrEmpty(instance.Loader) ||
                                  instance.Loader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase) ||
                                  loaders.Contains(instance.Loader.ToLowerInvariant()) ||
                                  !loaders.Any();

                if (verMatch && loaderMatch)
                {
                    selectedVersion = ver;
                    break;
                }
            }

            // If no exact match, fallback to first version
            selectedVersion ??= versionArray.FirstOrDefault();
            if (selectedVersion == null)
            {
                throw new InvalidOperationException($"No compatible versions found for modpack '{modpackId}'.");
            }

            var filesArray = selectedVersion["files"] as JArray;
            var primaryFile = filesArray?.FirstOrDefault(f => f["primary"]?.Value<bool>() == true) ?? filesArray?.FirstOrDefault();
            var mrpackUrl = primaryFile?["url"]?.ToString();
            var mrpackSize = primaryFile?["size"]?.Value<long>() ?? 0;
            var mrpackSha1 = primaryFile?["hashes"]?["sha1"]?.ToString();

            if (string.IsNullOrEmpty(mrpackUrl))
            {
                throw new InvalidOperationException("Modpack download URL not found in Modrinth metadata.");
            }

            // 2. Download .mrpack archive to temp
            var tempMrpackPath = Path.Combine(Path.GetTempPath(), $"vayu_modpack_{Guid.NewGuid():N}.mrpack");
            var mrpackDownload = new DownloadItem
            {
                Url = mrpackUrl,
                DestinationPath = tempMrpackPath,
                Sha1Hash = mrpackSha1,
                ExpectedSize = mrpackSize,
                Category = "Modpack",
                Description = $"{modpackId} modpack archive"
            };

            progress?.Report(new DownloadProgressInfo
            {
                CurrentFileName = Path.GetFileName(tempMrpackPath),
                CurrentOperation = "Downloading modpack package...",
                CompletedFiles = 0,
                TotalFiles = 1
            });

            bool downloadOk = await _downloadService.DownloadFileAsync(mrpackDownload, progress, ct);
            if (!downloadOk || !File.Exists(tempMrpackPath))
            {
                throw new InvalidOperationException("Failed to download Modrinth .mrpack archive.");
            }

            try
            {
                // 3. Extract and Parse modrinth.index.json
                using var archive = ZipFile.OpenRead(tempMrpackPath);
                var indexEntry = archive.GetEntry("modrinth.index.json");
                if (indexEntry == null)
                {
                    throw new InvalidOperationException("Corrupt modpack: modrinth.index.json not found in archive.");
                }

                using var indexStream = indexEntry.Open();
                using var reader = new StreamReader(indexStream);
                var indexJson = await reader.ReadToEndAsync(ct);
                var indexObj = JObject.Parse(indexJson);

                // Synchronize true Minecraft and Loader versions from modpack metadata
                var deps = indexObj["dependencies"] as JObject;
                if (deps != null)
                {
                    var realMcVersion = deps["minecraft"]?.ToString();
                    if (!string.IsNullOrEmpty(realMcVersion))
                    {
                        instance.MinecraftVersion = realMcVersion;
                    }

                    var fabricVer = deps["fabric-loader"]?.ToString();
                    if (!string.IsNullOrEmpty(fabricVer))
                    {
                        instance.Loader = "Fabric";
                        instance.LoaderVersion = fabricVer;
                    }
                    else if (deps["neoforge"] != null)
                    {
                        instance.Loader = "NeoForge";
                        instance.LoaderVersion = deps["neoforge"]?.ToString();
                    }
                    else if (deps["forge"] != null)
                    {
                        instance.Loader = "Forge";
                        instance.LoaderVersion = deps["forge"]?.ToString();
                    }
                    else if (deps["quilt-loader"] != null)
                    {
                        instance.Loader = "Quilt";
                        instance.LoaderVersion = deps["quilt-loader"]?.ToString();
                    }

                    try
                    {
                        var instanceService = ServiceLocator.Resolve<IInstanceService>();
                        if (instanceService != null)
                        {
                            await instanceService.SaveInstanceAsync(instance);
                        }
                    }
                    catch { }
                }

                var modpackFiles = indexObj["files"] as JArray;
                if (modpackFiles != null && modpackFiles.Count > 0)
                {
                    var fileDownloads = new List<DownloadItem>();
                    var gameDir = instance.GameDirectory;
                    Directory.CreateDirectory(gameDir);

                    foreach (var f in modpackFiles)
                    {
                        var relPath = f["path"]?.ToString();
                        var downloads = f["downloads"] as JArray;
                        var fileUrl = downloads?.FirstOrDefault()?.ToString();
                        var fileSize = f["fileSize"]?.Value<long>() ?? f["size"]?.Value<long>() ?? 0;
                        var sha1 = f["hashes"]?["sha1"]?.ToString();

                        // Skip server-only unsupported client files
                        var envObj = f["env"] as JObject;
                        var clientEnv = envObj?["client"]?.ToString();
                        if (clientEnv != null && clientEnv.Equals("unsupported", StringComparison.OrdinalIgnoreCase))
                        {
                            continue;
                        }

                        if (!string.IsNullOrEmpty(relPath) && !string.IsNullOrEmpty(fileUrl))
                        {
                            var destPath = Path.Combine(gameDir, relPath);
                            
                            // If file already exists and is non-empty, skip to save bandwidth unless hash check fails
                            if (File.Exists(destPath) && new FileInfo(destPath).Length > 0)
                            {
                                continue;
                            }

                            fileDownloads.Add(new DownloadItem
                            {
                                Url = fileUrl,
                                DestinationPath = destPath,
                                Sha1Hash = sha1,
                                ExpectedSize = fileSize,
                                Category = "ModpackFile",
                                Description = Path.GetFileName(destPath)
                            });
                        }
                    }

                    if (fileDownloads.Count > 0)
                    {
                        progress?.Report(new DownloadProgressInfo
                        {
                            CurrentFileName = "Modpack Mods",
                            CurrentOperation = $"Downloading {fileDownloads.Count} modpack files...",
                            CompletedFiles = 0,
                            TotalFiles = fileDownloads.Count
                        });

                        var batchResult = await _downloadService.DownloadBatchAsync(fileDownloads, 10, progress, ct);
                        if (!batchResult.Success && batchResult.FailedItems > 0)
                        {
                            CrashLogger.LogMessage($"[Modpack]: Warning: {batchResult.FailedItems} files had download errors: {string.Join(", ", batchResult.Errors.Take(3))}");
                        }
                    }
                }

                // 4. Extract Overrides & Client-Overrides
                foreach (var entry in archive.Entries)
                {
                    if (string.IsNullOrEmpty(entry.Name)) continue; // skip directory entries

                    string? relativePath = null;
                    if (entry.FullName.StartsWith("overrides/", StringComparison.OrdinalIgnoreCase))
                    {
                        relativePath = entry.FullName["overrides/".Length..];
                    }
                    else if (entry.FullName.StartsWith("client-overrides/", StringComparison.OrdinalIgnoreCase))
                    {
                        relativePath = entry.FullName["client-overrides/".Length..];
                    }

                    if (!string.IsNullOrEmpty(relativePath))
                    {
                        var destPath = Path.Combine(instance.GameDirectory, relativePath);
                        var parent = Path.GetDirectoryName(destPath);
                        if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                        entry.ExtractToFile(destPath, overwrite: true);
                    }
                }

                return true;
            }
            finally
            {
                if (File.Exists(tempMrpackPath))
                {
                    try { File.Delete(tempMrpackPath); } catch { }
                }
            }
        }
    }
}
