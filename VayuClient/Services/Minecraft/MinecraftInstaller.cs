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
using VayuClient.Models;
using VayuClient.Services.Download;
using VayuClient.Services.Version;

namespace VayuClient.Services.Minecraft
{
    public class MinecraftInstaller : IMinecraftInstaller
    {
        private readonly IDownloadService _downloadService;
        private readonly IVersionService _versionService;
        private readonly string _rootDataDir;
        private readonly string _versionsDir;
        private readonly string _librariesDir;
        private readonly string _assetsDir;

        public MinecraftInstaller(IDownloadService downloadService, IVersionService versionService)
        {
            _downloadService = downloadService;
            _versionService = versionService;

            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            _rootDataDir = Path.Combine(appData, "VayuClient");
            _versionsDir = Path.Combine(_rootDataDir, "versions");
            _librariesDir = Path.Combine(_rootDataDir, "libraries");
            _assetsDir = Path.Combine(_rootDataDir, "assets");

            Directory.CreateDirectory(_versionsDir);
            Directory.CreateDirectory(_librariesDir);
            Directory.CreateDirectory(_assetsDir);
        }

        public async Task<MojangVersionPackage> GetVersionPackageAsync(string versionId, CancellationToken ct = default)
        {
            var versionDir = Path.Combine(_versionsDir, versionId);
            var versionJsonPath = Path.Combine(versionDir, $"{versionId}.json");

            if (File.Exists(versionJsonPath))
            {
                try
                {
                    var cachedJson = await File.ReadAllTextAsync(versionJsonPath, ct);
                    var pkg = JsonConvert.DeserializeObject<MojangVersionPackage>(cachedJson);
                    if (pkg != null && !string.IsNullOrEmpty(pkg.Id))
                    {
                        return pkg;
                    }
                }
                catch { }
            }

            // Fetch from Mojang Manifest
            var manifestVersions = await _versionService.GetManifestVersionsAsync(forceRefresh: false);
            var versionEntry = manifestVersions.FirstOrDefault(v => string.Equals(v.Id, versionId, StringComparison.OrdinalIgnoreCase));

            if (versionEntry == null || string.IsNullOrEmpty(versionEntry.Url))
            {
                throw new InvalidOperationException($"Minecraft version '{versionId}' not found in official Mojang manifest.");
            }

            Directory.CreateDirectory(versionDir);
            var downloadItem = new DownloadItem
            {
                Url = versionEntry.Url,
                DestinationPath = versionJsonPath,
                Category = "Metadata",
                Description = $"Version Metadata for {versionId}"
            };

            bool ok = await _downloadService.DownloadFileAsync(downloadItem, null, ct);
            if (!ok || !File.Exists(versionJsonPath))
            {
                throw new InvalidOperationException($"Failed to download metadata for Minecraft {versionId} from {versionEntry.Url}");
            }

            var json = await File.ReadAllTextAsync(versionJsonPath, ct);
            var resultPkg = JsonConvert.DeserializeObject<MojangVersionPackage>(json);
            if (resultPkg == null || string.IsNullOrEmpty(resultPkg.Id))
            {
                throw new InvalidOperationException($"Invalid version metadata format for Minecraft {versionId}");
            }

            return resultPkg;
        }

        public bool IsLibraryAllowedOnWindows(MojangLibrary library)
        {
            if (library.Rules == null || library.Rules.Count == 0)
            {
                return true;
            }

            bool allowed = false;
            foreach (var rule in library.Rules)
            {
                bool isMatch = false;

                if (rule.Os == null)
                {
                    isMatch = true;
                }
                else if (string.Equals(rule.Os.Name, "windows", StringComparison.OrdinalIgnoreCase))
                {
                    isMatch = true;
                }

                if (isMatch)
                {
                    allowed = string.Equals(rule.Action, "allow", StringComparison.OrdinalIgnoreCase);
                }
            }

            return allowed;
        }

        public async Task<bool> InstallMinecraftAsync(
            string versionId,
            string instanceNativesDir,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default)
        {
            Directory.CreateDirectory(instanceNativesDir);

            // 1. Get Version Package
            progress?.Report(new DownloadProgressInfo
            {
                CurrentFileName = $"{versionId}.json",
                CurrentOperation = "Resolving Minecraft metadata...",
                CompletedFiles = 0,
                TotalFiles = 1
            });

            var pkg = await GetVersionPackageAsync(versionId, ct);

            // 2. Download Client JAR
            if (pkg.Downloads?.Client != null && !string.IsNullOrEmpty(pkg.Downloads.Client.Url))
            {
                var clientJarPath = Path.Combine(_versionsDir, versionId, $"{versionId}.jar");
                var clientDownload = new DownloadItem
                {
                    Url = pkg.Downloads.Client.Url,
                    DestinationPath = clientJarPath,
                    Sha1Hash = pkg.Downloads.Client.Sha1,
                    ExpectedSize = pkg.Downloads.Client.Size,
                    Category = "Client",
                    Description = $"Minecraft {versionId} Client"
                };

                bool clientOk = await _downloadService.DownloadFileAsync(clientDownload, progress, ct);
                if (!clientOk)
                {
                    throw new InvalidOperationException($"Failed to download Minecraft {versionId} client JAR.");
                }
            }

            // 3. Download Libraries & Natives
            var libraryDownloads = new List<DownloadItem>();
            var nativeZipsToExtract = new List<string>();

            foreach (var lib in pkg.Libraries)
            {
                if (!IsLibraryAllowedOnWindows(lib)) continue;

                // Main Artifact
                if (lib.Downloads?.Artifact != null && !string.IsNullOrEmpty(lib.Downloads.Artifact.Url))
                {
                    string libRelPath = lib.Downloads.Artifact.Path ?? GetMavenPath(lib.Name);
                    string libDestPath = Path.Combine(_librariesDir, libRelPath);

                    libraryDownloads.Add(new DownloadItem
                    {
                        Url = lib.Downloads.Artifact.Url,
                        DestinationPath = libDestPath,
                        Sha1Hash = lib.Downloads.Artifact.Sha1,
                        ExpectedSize = lib.Downloads.Artifact.Size,
                        Category = "Library",
                        Description = Path.GetFileName(libDestPath)
                    });
                }
                else if (!string.IsNullOrEmpty(lib.Name) && lib.Downloads?.Artifact == null)
                {
                    // Fallback for libraries with no explicit downloads block (e.g. Forge/Fabric)
                    string libRelPath = GetMavenPath(lib.Name);
                    string libDestPath = Path.Combine(_librariesDir, libRelPath);
                    if (!File.Exists(libDestPath))
                    {
                        string mavenUrl = $"https://libraries.minecraft.net/{libRelPath.Replace('\\', '/')}";
                        libraryDownloads.Add(new DownloadItem
                        {
                            Url = mavenUrl,
                            DestinationPath = libDestPath,
                            Category = "Library",
                            Description = Path.GetFileName(libDestPath)
                        });
                    }
                }

                // Native Classifiers (Windows)
                if (lib.Downloads?.Classifiers != null)
                {
                    string? nativeKey = null;
                    if (lib.Natives != null && lib.Natives.TryGetValue("windows", out var winClassifier))
                    {
                        nativeKey = winClassifier.Replace("${arch}", Environment.Is64BitOperatingSystem ? "64" : "32");
                    }

                    if (nativeKey == null)
                    {
                        nativeKey = lib.Downloads.Classifiers.Keys.FirstOrDefault(k => k.Contains("natives-windows"));
                    }

                    if (nativeKey != null && lib.Downloads.Classifiers.TryGetValue(nativeKey, out var nativeArtifact) && !string.IsNullOrEmpty(nativeArtifact.Url))
                    {
                        string nativeRelPath = nativeArtifact.Path ?? GetMavenPath($"{lib.Name}:{nativeKey}");
                        string nativeDestPath = Path.Combine(_librariesDir, nativeRelPath);

                        libraryDownloads.Add(new DownloadItem
                        {
                            Url = nativeArtifact.Url,
                            DestinationPath = nativeDestPath,
                            Sha1Hash = nativeArtifact.Sha1,
                            ExpectedSize = nativeArtifact.Size,
                            Category = "Native",
                            Description = Path.GetFileName(nativeDestPath)
                        });

                        nativeZipsToExtract.Add(nativeDestPath);
                    }
                }
            }

            if (libraryDownloads.Count > 0)
            {
                progress?.Report(new DownloadProgressInfo
                {
                    CurrentFileName = "Libraries",
                    CurrentOperation = $"Downloading {libraryDownloads.Count} game libraries...",
                    CompletedFiles = 0,
                    TotalFiles = libraryDownloads.Count
                });

                var libBatchResult = await _downloadService.DownloadBatchAsync(libraryDownloads, 12, progress, ct);
                if (!libBatchResult.Success)
                {
                    throw new InvalidOperationException($"Failed to download required libraries: {string.Join(", ", libBatchResult.Errors.Take(3))}");
                }
            }

            // Extract Natives to instance natives directory
            foreach (var nativeZip in nativeZipsToExtract)
            {
                if (File.Exists(nativeZip))
                {
                    try
                    {
                        using var zip = ZipFile.OpenRead(nativeZip);
                        foreach (var entry in zip.Entries)
                        {
                            if (entry.FullName.EndsWith(".dll", StringComparison.OrdinalIgnoreCase))
                            {
                                var destFile = Path.Combine(instanceNativesDir, Path.GetFileName(entry.FullName));
                                if (!File.Exists(destFile) || new FileInfo(destFile).Length != entry.Length)
                                {
                                    entry.ExtractToFile(destFile, overwrite: true);
                                }
                            }
                        }
                    }
                    catch { }
                }
            }

            // 4. Download Assets Index & Objects
            if (pkg.AssetIndex != null && !string.IsNullOrEmpty(pkg.AssetIndex.Url))
            {
                var indexDir = Path.Combine(_assetsDir, "indexes");
                Directory.CreateDirectory(indexDir);
                var indexFilePath = Path.Combine(indexDir, $"{pkg.AssetIndex.Id}.json");

                var indexDownload = new DownloadItem
                {
                    Url = pkg.AssetIndex.Url,
                    DestinationPath = indexFilePath,
                    Sha1Hash = pkg.AssetIndex.Sha1,
                    ExpectedSize = pkg.AssetIndex.Size,
                    Category = "AssetIndex",
                    Description = $"Asset index {pkg.AssetIndex.Id}"
                };

                bool indexOk = await _downloadService.DownloadFileAsync(indexDownload, progress, ct);
                if (indexOk && File.Exists(indexFilePath))
                {
                    var indexJson = await File.ReadAllTextAsync(indexFilePath, ct);
                    var assetIndex = JsonConvert.DeserializeObject<MojangAssetIndexFile>(indexJson);

                    if (assetIndex?.Objects != null && assetIndex.Objects.Count > 0)
                    {
                        var assetDownloads = new List<DownloadItem>();
                        var objectsDir = Path.Combine(_assetsDir, "objects");

                        foreach (var kvp in assetIndex.Objects)
                        {
                            var hash = kvp.Value.Hash;
                            var size = kvp.Value.Size;
                            if (string.IsNullOrEmpty(hash) || hash.Length < 2) continue;

                            var subDir = hash[..2];
                            var destPath = Path.Combine(objectsDir, subDir, hash);
                            var url = $"https://resources.download.minecraft.net/{subDir}/{hash}";

                            assetDownloads.Add(new DownloadItem
                            {
                                Url = url,
                                DestinationPath = destPath,
                                Sha1Hash = hash,
                                ExpectedSize = size,
                                Category = "Asset",
                                Description = kvp.Key
                            });
                        }

                        if (assetDownloads.Count > 0)
                        {
                            progress?.Report(new DownloadProgressInfo
                            {
                                CurrentFileName = "Assets",
                                CurrentOperation = $"Validating {assetDownloads.Count} game assets...",
                                CompletedFiles = 0,
                                TotalFiles = assetDownloads.Count
                            });

                            var assetBatchResult = await _downloadService.DownloadBatchAsync(assetDownloads, 16, progress, ct);
                            // Note: Legacy asset objects on Mojang CDN (e.g. pre-1.6 sounds) may return 404. Non-fatal for launch.
                        }
                    }
                }
            }

            progress?.Report(new DownloadProgressInfo
            {
                CurrentFileName = "Complete",
                CurrentOperation = "Minecraft installation complete",
                CompletedFiles = 1,
                TotalFiles = 1,
                BytesReceived = 1,
                TotalBytes = 1
            });

            return true;
        }

        public List<string> ResolveClasspath(MojangVersionPackage pkg, string instanceNativesDir)
        {
            var classpath = new List<string>();

            // 1. Libraries
            foreach (var lib in pkg.Libraries)
            {
                if (!IsLibraryAllowedOnWindows(lib)) continue;

                if (lib.Downloads?.Artifact != null)
                {
                    string libRelPath = lib.Downloads.Artifact.Path ?? GetMavenPath(lib.Name);
                    string libDestPath = Path.Combine(_librariesDir, libRelPath);
                    if (File.Exists(libDestPath))
                    {
                        classpath.Add(libDestPath);
                    }
                }
                else if (!string.IsNullOrEmpty(lib.Name))
                {
                    string libRelPath = GetMavenPath(lib.Name);
                    string libDestPath = Path.Combine(_librariesDir, libRelPath);
                    if (File.Exists(libDestPath))
                    {
                        classpath.Add(libDestPath);
                    }
                }
            }

            // 2. Client JAR
            var clientJarPath = Path.Combine(_versionsDir, pkg.Id, $"{pkg.Id}.jar");
            if (File.Exists(clientJarPath))
            {
                classpath.Add(clientJarPath);
            }

            return classpath;
        }

        private static string GetMavenPath(string mavenCoord)
        {
            // format: group:name:version[:classifier][@ext]
            var parts = mavenCoord.Split(':');
            if (parts.Length < 3) return mavenCoord.Replace(':', '/') + ".jar";

            var group = parts[0].Replace('.', Path.DirectorySeparatorChar);
            var artifact = parts[1];
            var version = parts[2];
            var classifier = parts.Length > 3 ? parts[3] : null;

            if (!string.IsNullOrEmpty(classifier))
            {
                return Path.Combine(group, artifact, version, $"{artifact}-{version}-{classifier}.jar");
            }

            return Path.Combine(group, artifact, version, $"{artifact}-{version}.jar");
        }
    }
}
