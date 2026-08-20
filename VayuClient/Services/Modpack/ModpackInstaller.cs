using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using System.Text.RegularExpressions;
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
                return await InstallLocalArchiveAsync(instance, tempMrpackPath, progress, ct);
            }
            finally
            {
                if (File.Exists(tempMrpackPath))
                {
                    try { File.Delete(tempMrpackPath); } catch { }
                }
            }
        }

        public async Task<bool> InstallLocalArchiveAsync(
            MinecraftInstance instance,
            string archivePath,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default)
        {
            if (!File.Exists(archivePath))
            {
                throw new FileNotFoundException("Modpack archive file not found", archivePath);
            }

            using var archive = ZipFile.OpenRead(archivePath);

            // 1. Check for Modrinth modpack (at root or nested in a folder like 881269a6-8c36-3215-905d-9d46a4602190/)
            var modrinthIndexEntry = archive.Entries.FirstOrDefault(e => e.FullName.EndsWith("modrinth.index.json", StringComparison.OrdinalIgnoreCase));
            if (modrinthIndexEntry != null)
            {
                return await ProcessModrinthArchiveAsync(instance, archive, modrinthIndexEntry, progress, ct);
            }

            // 2. Check for CurseForge manifest.json
            var curseforgeManifest = archive.Entries.FirstOrDefault(e => e.FullName.EndsWith("manifest.json", StringComparison.OrdinalIgnoreCase));
            if (curseforgeManifest != null)
            {
                return await ProcessCurseForgeArchiveAsync(instance, archive, curseforgeManifest, progress, ct);
            }

            // 3. Fallback: Raw Minecraft instance archive (stripping common single root folder if nested)
            return await ProcessRawInstanceArchiveAsync(instance, archive, progress, ct);
        }

        private async Task<bool> ProcessModrinthArchiveAsync(
            MinecraftInstance instance,
            ZipArchive archive,
            ZipArchiveEntry indexEntry,
            IProgress<DownloadProgressInfo>? progress,
            CancellationToken ct)
        {
            string rootPrefix = "";
            if (indexEntry.FullName.Length > "modrinth.index.json".Length)
            {
                rootPrefix = indexEntry.FullName[..^("modrinth.index.json".Length)];
            }

            using var indexStream = indexEntry.Open();
            using var reader = new StreamReader(indexStream);
            var indexJson = await reader.ReadToEndAsync(ct);
            var indexObj = JObject.Parse(indexJson);

            // Synchronize metadata
            var packName = indexObj["name"]?.ToString();
            if (!string.IsNullOrWhiteSpace(packName))
            {
                instance.Name = packName;
            }

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
                    instance.LoaderVersion = deps["neoforge"]?.ToString() ?? "";
                }
                else if (deps["forge"] != null)
                {
                    instance.Loader = "Forge";
                    instance.LoaderVersion = deps["forge"]?.ToString() ?? "";
                }
                else if (deps["quilt-loader"] != null)
                {
                    instance.Loader = "Quilt";
                    instance.LoaderVersion = deps["quilt-loader"]?.ToString() ?? "";
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

            var gameDir = instance.GameDirectory;
            Directory.CreateDirectory(gameDir);
            var modsDir = Path.Combine(gameDir, "mods");
            Directory.CreateDirectory(modsDir);

            // A. Download all remote mod files declared in files[]
            var modpackFiles = indexObj["files"] as JArray;
            if (modpackFiles != null && modpackFiles.Count > 0)
            {
                var fileDownloads = new List<DownloadItem>();

                foreach (var f in modpackFiles)
                {
                    var relPath = f["path"]?.ToString();
                    var downloads = f["downloads"] as JArray;
                    var fileUrl = downloads?.FirstOrDefault()?.ToString();
                    var fileSize = f["fileSize"]?.Value<long>() ?? f["size"]?.Value<long>() ?? 0;
                    var sha1 = f["hashes"]?["sha1"]?.ToString();

                    var envObj = f["env"] as JObject;
                    var clientEnv = envObj?["client"]?.ToString();
                    if (clientEnv != null && clientEnv.Equals("unsupported", StringComparison.OrdinalIgnoreCase))
                    {
                        continue;
                    }

                    if (!string.IsNullOrEmpty(relPath) && !string.IsNullOrEmpty(fileUrl))
                    {
                        var destPath = Path.Combine(gameDir, relPath);
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
                        CurrentOperation = $"Downloading {fileDownloads.Count} mods for {instance.Name}...",
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

            // B. Extract local files & overrides (unwrapping rootPrefix cleanly)
            foreach (var entry in archive.Entries)
            {
                if (string.IsNullOrEmpty(entry.Name) || entry.FullName.EndsWith("/")) continue;
                if (entry.FullName.Equals(indexEntry.FullName, StringComparison.OrdinalIgnoreCase)) continue;

                string fullName = entry.FullName;
                if (!string.IsNullOrEmpty(rootPrefix) && fullName.StartsWith(rootPrefix, StringComparison.OrdinalIgnoreCase))
                {
                    fullName = fullName[rootPrefix.Length..];
                }

                string relativePath = fullName;
                if (relativePath.StartsWith("overrides/", StringComparison.OrdinalIgnoreCase))
                {
                    relativePath = relativePath["overrides/".Length..];
                }
                else if (relativePath.StartsWith("client-overrides/", StringComparison.OrdinalIgnoreCase))
                {
                    relativePath = relativePath["client-overrides/".Length..];
                }

                // Ignore internal loader cache folders (e.g. .fabric/processedMods)
                if (relativePath.StartsWith(".fabric/", StringComparison.OrdinalIgnoreCase) || 
                    relativePath.StartsWith(".quilt/", StringComparison.OrdinalIgnoreCase) ||
                    relativePath.Contains("processedMods", StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                var destPath = Path.Combine(gameDir, relativePath);
                var parent = Path.GetDirectoryName(destPath);
                if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                entry.ExtractToFile(destPath, overwrite: true);
            }

            return true;
        }

        private async Task<bool> ProcessCurseForgeArchiveAsync(
            MinecraftInstance instance,
            ZipArchive archive,
            ZipArchiveEntry manifestEntry,
            IProgress<DownloadProgressInfo>? progress,
            CancellationToken ct)
        {
            string rootPrefix = "";
            if (manifestEntry.FullName.Length > "manifest.json".Length)
            {
                rootPrefix = manifestEntry.FullName[..^("manifest.json".Length)];
            }

            using var manifestStream = manifestEntry.Open();
            using var reader = new StreamReader(manifestStream);
            var manifestJson = await reader.ReadToEndAsync(ct);
            var manifestObj = JObject.Parse(manifestJson);

            var packName = manifestObj["name"]?.ToString();
            if (!string.IsNullOrWhiteSpace(packName))
            {
                instance.Name = packName;
            }

            var mcObj = manifestObj["minecraft"] as JObject;
            if (mcObj != null)
            {
                var mcVer = mcObj["version"]?.ToString();
                if (!string.IsNullOrEmpty(mcVer)) instance.MinecraftVersion = mcVer;

                var loaders = mcObj["modLoaders"] as JArray;
                var primaryLoader = loaders?.FirstOrDefault()?["id"]?.ToString();
                if (!string.IsNullOrEmpty(primaryLoader))
                {
                    if (primaryLoader.StartsWith("fabric-", StringComparison.OrdinalIgnoreCase))
                    {
                        instance.Loader = "Fabric";
                        instance.LoaderVersion = primaryLoader["fabric-".Length..];
                    }
                    else if (primaryLoader.StartsWith("forge-", StringComparison.OrdinalIgnoreCase))
                    {
                        instance.Loader = "Forge";
                        instance.LoaderVersion = primaryLoader["forge-".Length..];
                    }
                    else if (primaryLoader.StartsWith("neoforge-", StringComparison.OrdinalIgnoreCase))
                    {
                        instance.Loader = "NeoForge";
                        instance.LoaderVersion = primaryLoader["neoforge-".Length..];
                    }
                }
            }

            var gameDir = instance.GameDirectory;
            Directory.CreateDirectory(gameDir);

            // Extract overrides
            string overridesPrefix = manifestObj["overrides"]?.ToString() ?? "overrides";
            if (!overridesPrefix.EndsWith('/')) overridesPrefix += "/";

            foreach (var entry in archive.Entries)
            {
                if (string.IsNullOrEmpty(entry.Name) || entry.FullName.EndsWith("/")) continue;

                string fn = entry.FullName;
                if (!string.IsNullOrEmpty(rootPrefix) && fn.StartsWith(rootPrefix, StringComparison.OrdinalIgnoreCase))
                {
                    fn = fn[rootPrefix.Length..];
                }

                if (fn.StartsWith(overridesPrefix, StringComparison.OrdinalIgnoreCase))
                {
                    string rel = fn[overridesPrefix.Length..];
                    var dest = Path.Combine(gameDir, rel);
                    var parent = Path.GetDirectoryName(dest);
                    if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                    entry.ExtractToFile(dest, overwrite: true);
                }
            }

            return true;
        }

        private async Task<bool> ProcessRawInstanceArchiveAsync(
            MinecraftInstance instance,
            ZipArchive archive,
            IProgress<DownloadProgressInfo>? progress,
            CancellationToken ct)
        {
            var gameDir = instance.GameDirectory;
            Directory.CreateDirectory(gameDir);

            // 1. Inspect MultiMC / Prism instance.cfg
            var instanceCfgEntry = archive.Entries.FirstOrDefault(e => e.FullName.EndsWith("instance.cfg", StringComparison.OrdinalIgnoreCase));
            if (instanceCfgEntry != null)
            {
                try
                {
                    using var cfgStream = instanceCfgEntry.Open();
                    using var reader = new StreamReader(cfgStream);
                    string cfgText = await reader.ReadToEndAsync(ct);
                    foreach (var line in cfgText.Split('\n'))
                    {
                        var trimmed = line.Trim();
                        if (trimmed.StartsWith("IntendedVersion=", StringComparison.OrdinalIgnoreCase) ||
                            trimmed.StartsWith("MinecraftVersion=", StringComparison.OrdinalIgnoreCase) ||
                            trimmed.StartsWith("MCVersion=", StringComparison.OrdinalIgnoreCase))
                        {
                            var ver = trimmed.Split('=', 2)[1].Trim();
                            if (!string.IsNullOrEmpty(ver)) instance.MinecraftVersion = ver;
                        }
                        if (trimmed.StartsWith("name=", StringComparison.OrdinalIgnoreCase))
                        {
                            var name = trimmed.Split('=', 2)[1].Trim();
                            if (!string.IsNullOrEmpty(name)) instance.Name = name;
                        }
                    }
                }
                catch { }
            }

            // 2. Inspect mmc-pack.json / pack.json
            var mmcPackEntry = archive.Entries.FirstOrDefault(e => e.FullName.EndsWith("mmc-pack.json", StringComparison.OrdinalIgnoreCase) || e.FullName.EndsWith("pack.json", StringComparison.OrdinalIgnoreCase));
            if (mmcPackEntry != null)
            {
                try
                {
                    using var stream = mmcPackEntry.Open();
                    using var reader = new StreamReader(stream);
                    var json = await reader.ReadToEndAsync(ct);
                    var obj = JObject.Parse(json);
                    var components = obj["components"] as JArray;
                    if (components != null)
                    {
                        foreach (var c in components)
                        {
                            var uid = c["uid"]?.ToString() ?? c["cachedComponentId"]?.ToString();
                            var ver = c["version"]?.ToString();
                            if (uid == "net.minecraft" && !string.IsNullOrEmpty(ver))
                            {
                                instance.MinecraftVersion = ver;
                            }
                            if (uid == "net.fabricmc.fabric-loader" && !string.IsNullOrEmpty(ver))
                            {
                                instance.Loader = "Fabric";
                                instance.LoaderVersion = ver;
                            }
                        }
                    }
                }
                catch { }
            }

            // 3. Inspect mod JARs inside mods/ for fabric.mod.json / dependencies
            var modEntries = archive.Entries.Where(e => e.FullName.Contains("mods/") && e.FullName.EndsWith(".jar", StringComparison.OrdinalIgnoreCase)).Take(15).ToList();
            foreach (var modEntry in modEntries)
            {
                try
                {
                    using var modStream = modEntry.Open();
                    using var memoryStream = new MemoryStream();
                    await modStream.CopyToAsync(memoryStream, ct);
                    memoryStream.Position = 0;

                    using var modZip = new ZipArchive(memoryStream, ZipArchiveMode.Read);
                    var fabricJson = modZip.Entries.FirstOrDefault(e => e.FullName.Equals("fabric.mod.json", StringComparison.OrdinalIgnoreCase));
                    if (fabricJson != null)
                    {
                        using var fjStream = fabricJson.Open();
                        using var fjReader = new StreamReader(fjStream);
                        var fjText = await fjReader.ReadToEndAsync(ct);
                        var fjObj = JObject.Parse(fjText);
                        var depends = fjObj["depends"] as JObject;
                        if (depends != null)
                        {
                            var mcDep = depends["minecraft"]?.ToString();
                            if (!string.IsNullOrEmpty(mcDep))
                            {
                                var match = Regex.Match(mcDep, @"(26(\.\d+)*|1\.21(\.\d+)*)");
                                if (match.Success)
                                {
                                    instance.MinecraftVersion = match.Value;
                                    instance.Loader = "Fabric";
                                    break;
                                }
                            }
                        }
                    }
                }
                catch { }
            }

            // 4. Inspect instance name for explicit version tag (e.g. 26.2, 26.1, 1.21.4, etc.)
            if (string.IsNullOrEmpty(instance.MinecraftVersion) || instance.MinecraftVersion == "1.21.11")
            {
                var nameMatch = Regex.Match(instance.Name, @"\b(26(\.\d+)*|1\.21(\.\d+)*)\b");
                if (nameMatch.Success)
                {
                    instance.MinecraftVersion = nameMatch.Value;
                }
            }

            // Detect if all files share a common root directory
            var firstEntry = archive.Entries.FirstOrDefault(e => !string.IsNullOrEmpty(e.Name));
            string commonPrefix = "";
            if (firstEntry != null && firstEntry.FullName.Contains('/'))
            {
                var candidate = firstEntry.FullName[..(firstEntry.FullName.IndexOf('/') + 1)];
                if (archive.Entries.All(e => string.IsNullOrEmpty(e.Name) || e.FullName.StartsWith(candidate, StringComparison.OrdinalIgnoreCase)))
                {
                    commonPrefix = candidate;
                }
            }

            foreach (var entry in archive.Entries)
            {
                if (string.IsNullOrEmpty(entry.Name) || entry.FullName.EndsWith("/")) continue;

                string rel = entry.FullName;
                if (!string.IsNullOrEmpty(commonPrefix) && rel.StartsWith(commonPrefix, StringComparison.OrdinalIgnoreCase))
                {
                    rel = rel[commonPrefix.Length..];
                }

                // Ignore internal loader cache folders (e.g. .fabric/processedMods)
                if (rel.StartsWith(".fabric/", StringComparison.OrdinalIgnoreCase) || 
                    rel.StartsWith(".quilt/", StringComparison.OrdinalIgnoreCase) ||
                    rel.Contains("processedMods", StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                var dest = Path.Combine(gameDir, rel);
                var parent = Path.GetDirectoryName(dest);
                if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                entry.ExtractToFile(dest, overwrite: true);
            }

            // Persist updated instance properties
            try
            {
                var instanceService = ServiceLocator.Resolve<IInstanceService>();
                if (instanceService != null)
                {
                    await instanceService.SaveInstanceAsync(instance);
                }
            }
            catch { }

            return true;
        }
    }
}
