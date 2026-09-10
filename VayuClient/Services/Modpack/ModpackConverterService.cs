using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Download;
using VayuClient.Services.Instance;

namespace VayuClient.Services.Modpack
{
    public class ModpackConverterService : IModpackConverterService
    {
        private readonly IDownloadService _downloadService;
        private readonly IInstanceService _instanceService;

        private static readonly HttpClient _http = new()
        {
            Timeout = TimeSpan.FromSeconds(25)
        };

        static ModpackConverterService()
        {
            if (!_http.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _http.DefaultRequestHeaders.Add("User-Agent", AppInfo.UserAgent);
            }
        }

        public ModpackConverterService(IDownloadService downloadService, IInstanceService instanceService)
        {
            _downloadService = downloadService;
            _instanceService = instanceService;
        }

        public async Task<ConversionResult> ConvertInstanceAsync(
            MinecraftInstance sourceInstance,
            ConversionOptions options,
            IProgress<ConversionProgressInfo>? progress = null,
            CancellationToken ct = default)
        {
            var result = new ConversionResult
            {
                SourceInstance = sourceInstance
            };

            if (sourceInstance == null)
            {
                result.Success = false;
                result.ErrorMessage = "Source instance is null.";
                return result;
            }

            if (string.IsNullOrWhiteSpace(options.TargetMinecraftVersion))
            {
                result.Success = false;
                result.ErrorMessage = "Target Minecraft version must be specified.";
                return result;
            }

            string targetVersion = options.TargetMinecraftVersion.Trim();
            string targetLoader = string.IsNullOrWhiteSpace(options.TargetLoader) ? "Fabric" : options.TargetLoader.Trim();

            try
            {
                // ─── 1. SCAN SOURCE MODS ────────────────────────────────────────────────
                progress?.Report(new ConversionProgressInfo
                {
                    Phase = ConversionPhase.Scanning,
                    CurrentOperation = "Scanning source mods...",
                    CurrentItem = sourceInstance.Name,
                    ProcessedCount = 0,
                    TotalCount = 1,
                    Percent = 5.0
                });

                var sourceModFiles = FindSourceModJars(sourceInstance);
                CrashLogger.LogMessage($"[Converter]: Found {sourceModFiles.Count} mod JARs in source instance '{sourceInstance.Name}'");

                var parsedMods = new List<SourceModMetadata>();
                foreach (var jarPath in sourceModFiles)
                {
                    var meta = ParseModMetadata(jarPath);
                    if (meta != null && !IsInternalVayuMod(meta))
                    {
                        parsedMods.Add(meta);
                    }
                }

                // Deduplicate by ModId or Name
                var distinctMods = parsedMods
                    .GroupBy(m => string.IsNullOrEmpty(m.ModId) ? m.DisplayName.ToLowerInvariant() : m.ModId.ToLowerInvariant())
                    .Select(g => g.First())
                    .ToList();

                int totalMods = distinctMods.Count;

                // ─── 2. RESOLVE COMPATIBLE MODS VIA MODRINTH API ─────────────────────────
                progress?.Report(new ConversionProgressInfo
                {
                    Phase = ConversionPhase.ResolvingModrinth,
                    CurrentOperation = $"Resolving {totalMods} mods on Modrinth for MC {targetVersion} ({targetLoader})...",
                    CurrentItem = string.Empty,
                    ProcessedCount = 0,
                    TotalCount = totalMods,
                    Percent = 10.0
                });

                var pendingDownloads = new List<DownloadItem>();
                int resolvedIndex = 0;

                foreach (var mod in distinctMods)
                {
                    ct.ThrowIfCancellationRequested();
                    resolvedIndex++;

                    double resolvePercent = 10.0 + (resolvedIndex / (double)Math.Max(1, totalMods)) * 40.0;
                    progress?.Report(new ConversionProgressInfo
                    {
                        Phase = ConversionPhase.ResolvingModrinth,
                        CurrentOperation = $"Resolving mod [{resolvedIndex}/{totalMods}]: {mod.DisplayName}",
                        CurrentItem = mod.DisplayName,
                        ProcessedCount = resolvedIndex,
                        TotalCount = totalMods,
                        Percent = resolvePercent
                    });

                    var match = await ResolveModOnModrinthAsync(mod, targetVersion, targetLoader, ct);
                    if (match != null && match.IsCompatible && !string.IsNullOrEmpty(match.DownloadUrl))
                    {
                        result.PortedMods.Add(new PortedModInfo
                        {
                            ModName = mod.DisplayName,
                            ModId = mod.ModId,
                            OriginalFileName = Path.GetFileName(mod.FilePath),
                            NewFileName = match.FileName,
                            TargetVersion = match.VersionNumber,
                            DownloadUrl = match.DownloadUrl,
                            FileSize = match.FileSize
                        });
                    }
                    else
                    {
                        string reason = match?.FailureReason 
                            ?? $"No compatible release found on Modrinth for Minecraft {targetVersion} ({targetLoader})";

                        result.MissingMods.Add(new MissingModInfo
                        {
                            ModName = mod.DisplayName,
                            ModId = mod.ModId,
                            OriginalFileName = Path.GetFileName(mod.FilePath),
                            Reason = reason,
                            ModrinthSearchUrl = $"https://modrinth.com/mods?q={Uri.EscapeDataString(mod.DisplayName)}"
                        });
                    }

                    // Small delay to respect Modrinth API limits
                    await Task.Delay(30, ct);
                }

                // ─── 3. CREATE TARGET CONVERTED INSTANCE ─────────────────────────────────
                progress?.Report(new ConversionProgressInfo
                {
                    Phase = ConversionPhase.Finalizing,
                    CurrentOperation = "Creating target instance...",
                    CurrentItem = options.NewInstanceName,
                    ProcessedCount = resolvedIndex,
                    TotalCount = totalMods,
                    Percent = 55.0
                });

                string cleanNewName = string.IsNullOrWhiteSpace(options.NewInstanceName)
                    ? $"{sourceInstance.Name} ({targetVersion} {targetLoader})"
                    : options.NewInstanceName.Trim();

                var newInstance = new MinecraftInstance
                {
                    InstanceId = Guid.NewGuid().ToString("N"),
                    Name = cleanNewName,
                    MinecraftVersion = targetVersion,
                    Loader = targetLoader,
                    LoaderVersion = string.Empty,
                    RamMB = options.RamMB > 0 ? options.RamMB : sourceInstance.RamMB,
                    JvmArguments = sourceInstance.JvmArguments,
                    ModpackId = sourceInstance.ModpackId,
                    ModpackVersion = sourceInstance.ModpackVersion,
                    Icon = sourceInstance.Icon,
                    ArtworkPath = sourceInstance.ArtworkPath,
                    PerformanceProfile = sourceInstance.PerformanceProfile,
                    CreatedAt = DateTime.UtcNow,
                    IsActive = false
                };

                newInstance = await _instanceService.CreateInstanceAsync(newInstance);
                result.ConvertedInstance = newInstance;

                var targetGameDir = newInstance.GameDirectory;
                var targetModsDir = Path.Combine(targetGameDir, "mods");
                Directory.CreateDirectory(targetModsDir);

                // ─── 4. COPY RESOURCE PACKS, SHADERS & OPTIONS ───────────────────────────
                if (options.CopyResourcePacks || options.CopyShaderPacks || options.CopyOptions)
                {
                    progress?.Report(new ConversionProgressInfo
                    {
                        Phase = ConversionPhase.CopyingAssets,
                        CurrentOperation = "Copying Resource Packs, Shaders, and configs...",
                        CurrentItem = string.Empty,
                        ProcessedCount = 0,
                        TotalCount = 1,
                        Percent = 60.0
                    });

                    CopyInstanceAssets(sourceInstance, newInstance, options, result);
                }

                // ─── 5. DOWNLOAD COMPATIBLE MODS INTO NEW INSTANCE ────────────────────────
                if (result.PortedMods.Count > 0)
                {
                    foreach (var ported in result.PortedMods)
                    {
                        var destPath = Path.Combine(targetModsDir, ported.NewFileName);
                        pendingDownloads.Add(new DownloadItem
                        {
                            Url = ported.DownloadUrl,
                            DestinationPath = destPath,
                            ExpectedSize = ported.FileSize,
                            Category = "Mod",
                            Description = ported.ModName
                        });
                    }

                    progress?.Report(new ConversionProgressInfo
                    {
                        Phase = ConversionPhase.Downloading,
                        CurrentOperation = $"Downloading {pendingDownloads.Count} compatible mods...",
                        CurrentItem = string.Empty,
                        ProcessedCount = 0,
                        TotalCount = pendingDownloads.Count,
                        Percent = 65.0
                    });

                    var batchProgress = new Progress<DownloadProgressInfo>(d =>
                    {
                        double dlPercent = 65.0 + (d.CompletedFiles / (double)Math.Max(1, d.TotalFiles)) * 30.0;
                        progress?.Report(new ConversionProgressInfo
                        {
                            Phase = ConversionPhase.Downloading,
                            CurrentOperation = $"Downloading mod [{d.CompletedFiles}/{d.TotalFiles}]: {d.CurrentFileName}",
                            CurrentItem = d.CurrentFileName,
                            ProcessedCount = d.CompletedFiles,
                            TotalCount = d.TotalFiles,
                            Percent = Math.Min(95.0, dlPercent)
                        });
                    });

                    var batchResult = await _downloadService.DownloadBatchAsync(pendingDownloads, 6, batchProgress, ct);
                    if (!batchResult.Success && batchResult.FailedItems > 0)
                    {
                        CrashLogger.LogMessage($"[Converter]: Warning: {batchResult.FailedItems} mods had download failures.");
                    }
                }

                // ─── 6. AUTO-DEPLOY VAYUCLIENT UNIVERSAL HUD ─────────────────────────────
                DeployUniversalVayuHud(newInstance, targetVersion);

                // ─── 7. FINAL COMPLETION ────────────────────────────────────────────────
                progress?.Report(new ConversionProgressInfo
                {
                    Phase = ConversionPhase.Completed,
                    CurrentOperation = $"Conversion complete! {result.PortedMods.Count} mods ported, {result.MissingMods.Count} missing.",
                    CurrentItem = newInstance.Name,
                    ProcessedCount = totalMods,
                    TotalCount = totalMods,
                    Percent = 100.0
                });

                result.Success = true;
                CrashLogger.LogMessage($"[Converter]: Successfully converted instance '{sourceInstance.Name}' to '{newInstance.Name}' (MC {targetVersion} {targetLoader}). Ported: {result.PortedMods.Count}, Missing: {result.MissingMods.Count}");
                return result;
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ModpackConverterService.ConvertInstanceAsync", ex);
                result.Success = false;
                result.ErrorMessage = ex.Message;
                progress?.Report(new ConversionProgressInfo
                {
                    Phase = ConversionPhase.Failed,
                    CurrentOperation = $"Conversion failed: {ex.Message}",
                    Percent = 0.0
                });
                return result;
            }
        }

        private static List<string> FindSourceModJars(MinecraftInstance instance)
        {
            var results = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            var candidates = new List<string>();

            if (!string.IsNullOrEmpty(instance.GameDirectory))
            {
                candidates.Add(Path.Combine(instance.GameDirectory, "mods"));
                var parent = Path.GetDirectoryName(instance.GameDirectory);
                if (!string.IsNullOrEmpty(parent))
                {
                    candidates.Add(Path.Combine(parent, "mods"));
                }
            }

            foreach (var dir in candidates)
            {
                if (Directory.Exists(dir))
                {
                    foreach (var file in Directory.GetFiles(dir, "*.jar"))
                    {
                        results.Add(file);
                    }
                    foreach (var file in Directory.GetFiles(dir, "*.jar.disabled"))
                    {
                        results.Add(file);
                    }
                }
            }

            return results.ToList();
        }

        private static bool IsInternalVayuMod(SourceModMetadata meta)
        {
            if (string.IsNullOrEmpty(meta.ModId) && string.IsNullOrEmpty(meta.DisplayName)) return false;
            string id = (meta.ModId ?? "").ToLowerInvariant();
            string name = (meta.DisplayName ?? "").ToLowerInvariant();
            string file = (Path.GetFileName(meta.FilePath) ?? "").ToLowerInvariant();

            return id.Contains("vayuclient") || id.Contains("fastclient") 
                || name.Contains("vayuclient") || name.Contains("fastclient")
                || file.Contains("vayuclient-hud") || file.Contains("fastclient-hud");
        }

        private static SourceModMetadata? ParseModMetadata(string filePath)
        {
            if (!File.Exists(filePath)) return null;

            string fileName = Path.GetFileName(filePath);
            string cleanName = fileName.Replace(".jar.disabled", "").Replace(".jar", "");
            string modId = string.Empty;
            string version = "1.0";
            string sha1 = string.Empty;

            try
            {
                using var fs = File.OpenRead(filePath);
                using var sha = SHA1.Create();
                sha1 = Convert.ToHexString(sha.ComputeHash(fs)).ToLowerInvariant();
            }
            catch { }

            try
            {
                using var zip = ZipFile.OpenRead(filePath);

                // 1. Fabric
                var fabricEntry = zip.GetEntry("fabric.mod.json");
                if (fabricEntry != null)
                {
                    using var s = fabricEntry.Open();
                    using var r = new StreamReader(s);
                    var jObj = JObject.Parse(r.ReadToEnd());
                    modId = jObj["id"]?.ToString() ?? modId;
                    cleanName = jObj["name"]?.ToString() ?? cleanName;
                    version = jObj["version"]?.ToString() ?? version;
                }
                else
                {
                    // 2. Quilt
                    var quiltEntry = zip.GetEntry("quilt.mod.json");
                    if (quiltEntry != null)
                    {
                        using var s = quiltEntry.Open();
                        using var r = new StreamReader(s);
                        var jObj = JObject.Parse(r.ReadToEnd());
                        var meta = jObj["quilt_loader"]?["metadata"];
                        modId = meta?["id"]?.ToString() ?? modId;
                        cleanName = meta?["name"]?.ToString() ?? cleanName;
                        version = meta?["version"]?.ToString() ?? version;
                    }
                    else
                    {
                        // 3. NeoForge / Forge
                        var neoforgeEntry = zip.GetEntry("META-INF/neoforge.mods.toml");
                        var forgeEntry = zip.GetEntry("META-INF/mods.toml");
                        if (neoforgeEntry != null || forgeEntry != null)
                        {
                            var targetEntry = neoforgeEntry ?? forgeEntry;
                            using var s = targetEntry!.Open();
                            using var r = new StreamReader(s);
                            string toml = r.ReadToEnd();
                            var modIdMatch = Regex.Match(toml, @"modId\s*=\s*""([^""]+)""");
                            if (modIdMatch.Success) modId = modIdMatch.Groups[1].Value;
                            var nameMatch = Regex.Match(toml, @"displayName\s*=\s*""([^""]+)""");
                            if (nameMatch.Success) cleanName = nameMatch.Groups[1].Value;
                            var verMatch = Regex.Match(toml, @"version\s*=\s*""([^""]+)""");
                            if (verMatch.Success) version = verMatch.Groups[1].Value;
                        }
                    }
                }
            }
            catch { }

            return new SourceModMetadata
            {
                FilePath = filePath,
                ModId = modId,
                DisplayName = cleanName,
                Version = version,
                Sha1 = sha1
            };
        }

        private async Task<ModrinthResolutionResult?> ResolveModOnModrinthAsync(
            SourceModMetadata mod,
            string targetMcVersion,
            string targetLoader,
            CancellationToken ct)
        {
            try
            {
                string projectId = string.Empty;

                // 1. Try Modrinth Hash Lookup if SHA1 is present
                if (!string.IsNullOrEmpty(mod.Sha1))
                {
                    try
                    {
                        string hashUrl = $"https://api.modrinth.com/v2/version_file/{mod.Sha1}";
                        var hashRes = await _http.GetAsync(hashUrl, ct);
                        if (hashRes.IsSuccessStatusCode)
                        {
                            var hashJson = await hashRes.Content.ReadAsStringAsync(ct);
                            var hashObj = JObject.Parse(hashJson);
                            projectId = hashObj["project_id"]?.ToString() ?? string.Empty;
                        }
                    }
                    catch { }
                }

                // 2. Query Project Versions if Project ID is resolved
                if (!string.IsNullOrEmpty(projectId))
                {
                    var projectMatch = await QueryProjectVersionsAsync(projectId, targetMcVersion, targetLoader, ct);
                    if (projectMatch != null && projectMatch.IsCompatible)
                    {
                        return projectMatch;
                    }
                }

                // 3. Fallback to direct Mod ID lookup if project ID wasn't found or had no versions
                if (!string.IsNullOrEmpty(mod.ModId))
                {
                    var modIdMatch = await QueryProjectVersionsAsync(mod.ModId, targetMcVersion, targetLoader, ct);
                    if (modIdMatch != null && modIdMatch.IsCompatible)
                    {
                        return modIdMatch;
                    }
                }

                // 4. Search Modrinth by Display Name / Clean Query
                string cleanQuery = CleanModSearchQuery(mod.DisplayName);
                string loaderFacet = !string.IsNullOrEmpty(targetLoader) && !targetLoader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase)
                    ? $",[\"categories:{targetLoader.ToLowerInvariant()}\"]"
                    : "";

                string searchUrl = $"https://api.modrinth.com/v2/search?query={Uri.EscapeDataString(cleanQuery)}&facets=[[\"project_type:mod\"],[\"versions:{Uri.EscapeDataString(targetMcVersion)}\"]{loaderFacet}]&limit=3";
                var searchRes = await _http.GetAsync(searchUrl, ct);
                if (searchRes.IsSuccessStatusCode)
                {
                    var searchJson = await searchRes.Content.ReadAsStringAsync(ct);
                    var searchObj = JObject.Parse(searchJson);
                    var hits = searchObj["hits"] as JArray;
                    if (hits != null && hits.Count > 0)
                    {
                        var firstHit = hits[0];
                        var hitProjectId = firstHit["project_id"]?.ToString() ?? firstHit["slug"]?.ToString();
                        if (!string.IsNullOrEmpty(hitProjectId))
                        {
                            var hitMatch = await QueryProjectVersionsAsync(hitProjectId, targetMcVersion, targetLoader, ct);
                            if (hitMatch != null && hitMatch.IsCompatible)
                            {
                                return hitMatch;
                            }
                        }
                    }
                }

                return new ModrinthResolutionResult
                {
                    IsCompatible = false,
                    FailureReason = $"No release found on Modrinth for Minecraft {targetMcVersion} ({targetLoader})"
                };
            }
            catch (Exception ex)
            {
                return new ModrinthResolutionResult
                {
                    IsCompatible = false,
                    FailureReason = $"Modrinth lookup error: {ex.Message}"
                };
            }
        }

        private async Task<ModrinthResolutionResult?> QueryProjectVersionsAsync(
            string projectIdOrSlug,
            string targetMcVersion,
            string targetLoader,
            CancellationToken ct)
        {
            try
            {
                string encodedId = Uri.EscapeDataString(projectIdOrSlug);
                string versionsUrl = $"https://api.modrinth.com/v2/project/{encodedId}/version";
                var res = await _http.GetAsync(versionsUrl, ct);
                if (!res.IsSuccessStatusCode) return null;

                var json = await res.Content.ReadAsStringAsync(ct);
                var versions = JArray.Parse(json);
                if (versions.Count == 0) return null;

                foreach (var ver in versions)
                {
                    var gameVersions = ver["game_versions"]?.Select(v => v.ToString()) ?? Enumerable.Empty<string>();
                    var loaders = ver["loaders"]?.Select(l => l.ToString().ToLowerInvariant()) ?? Enumerable.Empty<string>();

                    bool verMatch = gameVersions.Contains(targetMcVersion) || !gameVersions.Any();
                    bool loaderMatch = string.IsNullOrEmpty(targetLoader) ||
                                      targetLoader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase) ||
                                      loaders.Contains(targetLoader.ToLowerInvariant()) ||
                                      (targetLoader.Equals("Quilt", StringComparison.OrdinalIgnoreCase) && loaders.Contains("fabric")) ||
                                      !loaders.Any();

                    if (verMatch && loaderMatch)
                    {
                        var files = ver["files"] as JArray;
                        var primary = files?.FirstOrDefault(f => f["primary"]?.Value<bool>() == true) ?? files?.FirstOrDefault();
                        if (primary != null)
                        {
                            return new ModrinthResolutionResult
                            {
                                IsCompatible = true,
                                VersionNumber = ver["version_number"]?.ToString() ?? "latest",
                                FileName = primary["filename"]?.ToString() ?? $"{projectIdOrSlug}-{targetMcVersion}.jar",
                                DownloadUrl = primary["url"]?.ToString() ?? string.Empty,
                                FileSize = primary["size"]?.Value<long>() ?? 0,
                                Sha1 = primary["hashes"]?["sha1"]?.ToString() ?? string.Empty
                            };
                        }
                    }
                }

                return new ModrinthResolutionResult
                {
                    IsCompatible = false,
                    FailureReason = $"Project '{projectIdOrSlug}' exists but has no version released for MC {targetMcVersion} ({targetLoader})"
                };
            }
            catch
            {
                return null;
            }
        }

        private static string CleanModSearchQuery(string displayName)
        {
            if (string.IsNullOrEmpty(displayName)) return string.Empty;
            var clean = Regex.Replace(displayName, @"[-_]+", " ");
            clean = Regex.Replace(clean, @"\b(v?\d+(\.\d+)+[a-zA-Z0-9_\-\.]*)\b", "");
            clean = Regex.Replace(clean, @"\b(fabric|forge|neoforge|quilt|mc\d+[\d\.]*)\b", "", RegexOptions.IgnoreCase);
            return clean.Trim();
        }

        private static void CopyInstanceAssets(
            MinecraftInstance source,
            MinecraftInstance target,
            ConversionOptions options,
            ConversionResult result)
        {
            var sourceGameDir = source.GameDirectory;
            var targetGameDir = target.GameDirectory;
            var sourceInstDir = Path.GetDirectoryName(sourceGameDir);
            var targetInstDir = Path.GetDirectoryName(targetGameDir);

            // 1. Copy Resource Packs
            if (options.CopyResourcePacks)
            {
                var sourceRp = Path.Combine(sourceGameDir, "resourcepacks");
                if (!Directory.Exists(sourceRp) && !string.IsNullOrEmpty(sourceInstDir))
                {
                    sourceRp = Path.Combine(sourceInstDir, "resourcepacks");
                }

                if (Directory.Exists(sourceRp))
                {
                    var targetRp = Path.Combine(targetGameDir, "resourcepacks");
                    Directory.CreateDirectory(targetRp);

                    foreach (var file in Directory.GetFiles(sourceRp))
                    {
                        var dest = Path.Combine(targetRp, Path.GetFileName(file));
                        try
                        {
                            File.Copy(file, dest, true);
                            result.CopiedResourcePacks.Add(Path.GetFileName(file));
                        }
                        catch { }
                    }
                    foreach (var dir in Directory.GetDirectories(sourceRp))
                    {
                        var destDir = Path.Combine(targetRp, Path.GetFileName(dir));
                        CopyDirectory(dir, destDir);
                        result.CopiedResourcePacks.Add(Path.GetFileName(dir) + " (folder)");
                    }
                }
            }

            // 2. Copy Shader Packs
            if (options.CopyShaderPacks)
            {
                var sourceSp = Path.Combine(sourceGameDir, "shaderpacks");
                if (!Directory.Exists(sourceSp) && !string.IsNullOrEmpty(sourceInstDir))
                {
                    sourceSp = Path.Combine(sourceInstDir, "shaderpacks");
                }

                if (Directory.Exists(sourceSp))
                {
                    var targetSp = Path.Combine(targetGameDir, "shaderpacks");
                    Directory.CreateDirectory(targetSp);

                    foreach (var file in Directory.GetFiles(sourceSp))
                    {
                        var dest = Path.Combine(targetSp, Path.GetFileName(file));
                        try
                        {
                            File.Copy(file, dest, true);
                            result.CopiedShaderPacks.Add(Path.GetFileName(file));
                        }
                        catch { }
                    }
                    foreach (var dir in Directory.GetDirectories(sourceSp))
                    {
                        var destDir = Path.Combine(targetSp, Path.GetFileName(dir));
                        CopyDirectory(dir, destDir);
                        result.CopiedShaderPacks.Add(Path.GetFileName(dir) + " (folder)");
                    }
                }
            }

            // 3. Copy Options / Settings
            if (options.CopyOptions)
            {
                var optionsTxt = Path.Combine(sourceGameDir, "options.txt");
                if (File.Exists(optionsTxt))
                {
                    try
                    {
                        File.Copy(optionsTxt, Path.Combine(targetGameDir, "options.txt"), true);
                    }
                    catch { }
                }
            }
        }

        private static void CopyDirectory(string sourceDir, string destDir)
        {
            Directory.CreateDirectory(destDir);
            foreach (var file in Directory.GetFiles(sourceDir))
            {
                var destFile = Path.Combine(destDir, Path.GetFileName(file));
                try { File.Copy(file, destFile, true); } catch { }
            }
            foreach (var sub in Directory.GetDirectories(sourceDir))
            {
                CopyDirectory(sub, Path.Combine(destDir, Path.GetFileName(sub)));
            }
        }

        private static void DeployUniversalVayuHud(MinecraftInstance instance, string mcVersion)
        {
            try
            {
                var targetModsDir = Path.Combine(instance.GameDirectory, "mods");
                Directory.CreateDirectory(targetModsDir);

                // Look for universal mod in app storage
                var candidateDirs = new List<string>
                {
                    Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "Assets", "Mods"),
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Assets", "Mods"),
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "VayuClient", "Assets", "Mods")
                };

                string expectedJarName = $"vayuclient-hud-2.1.0-mc{mcVersion}-universal.jar";
                string? sourceJar = null;

                foreach (var dir in candidateDirs)
                {
                    var exact = Path.Combine(dir, expectedJarName);
                    if (File.Exists(exact))
                    {
                        sourceJar = exact;
                        break;
                    }

                    if (Directory.Exists(dir))
                    {
                        var match = Directory.GetFiles(dir, $"vayuclient-hud-*-mc{mcVersion}*.jar").FirstOrDefault();
                        if (match != null)
                        {
                            sourceJar = match;
                            break;
                        }
                    }
                }

                if (!string.IsNullOrEmpty(sourceJar) && File.Exists(sourceJar))
                {
                    var dest = Path.Combine(targetModsDir, Path.GetFileName(sourceJar));
                    File.Copy(sourceJar, dest, true);
                    CrashLogger.LogMessage($"[Converter]: Auto-deployed VayuClient Universal HUD ({Path.GetFileName(sourceJar)}) to converted instance '{instance.Name}'");
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogMessage($"[Converter]: Note: Universal HUD deployment skipped ({ex.Message})");
            }
        }

        private class SourceModMetadata
        {
            public string FilePath { get; set; } = string.Empty;
            public string ModId { get; set; } = string.Empty;
            public string DisplayName { get; set; } = string.Empty;
            public string Version { get; set; } = string.Empty;
            public string Sha1 { get; set; } = string.Empty;
        }

        private class ModrinthResolutionResult
        {
            public bool IsCompatible { get; set; }
            public string VersionNumber { get; set; } = string.Empty;
            public string FileName { get; set; } = string.Empty;
            public string DownloadUrl { get; set; } = string.Empty;
            public long FileSize { get; set; }
            public string Sha1 { get; set; } = string.Empty;
            public string? FailureReason { get; set; }
        }
    }
}
