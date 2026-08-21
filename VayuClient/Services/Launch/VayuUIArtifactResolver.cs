using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Backend;
using VayuClient.Services.Java;

namespace VayuClient.Services.Launch
{
    public class VayuUIArtifactResolver : IVayuUIArtifactResolver
    {
        private readonly IVayuUiCompatibilityValidator _validator;
        private static readonly HttpClient _httpClient = new HttpClient
        {
            Timeout = TimeSpan.FromSeconds(30)
        };

        public VayuUIArtifactResolver(IVayuUiCompatibilityValidator? validator = null)
        {
            _validator = validator ?? new VayuUiCompatibilityValidator();
        }

        public async Task<string?> ResolveAndDeployAsync(MinecraftInstance instance, JavaRuntimeInfo javaRuntime)
        {
            if (instance == null) return null;

            string mcVersion = instance.MinecraftVersion ?? "1.21.4";
            string loader = instance.Loader?.ToString() ?? "Fabric";
            int jvmMajor = javaRuntime?.MajorVersion ?? 21;

            CrashLogger.LogMessage($"[VayuHUD Resolver] Dynamic resolution requested for Minecraft {mcVersion}, Loader {loader}, JVM Java {jvmMajor}...");

            // 1. Locate instance mods directory
            string instPath = instance.GameDirectory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Instances", instance.Name ?? "Default");
            string modsDir = Path.Combine(instPath, "mods");
            Directory.CreateDirectory(modsDir);

            // 2. Clean any stale or obsolete UI/HUD mods first
            _validator.PurgeIncompatibleUiMods(modsDir, jvmMajor, mcVersion, loader);

            // 3. Search asset stores for artifacts and manifest
            string appBase = AppDomain.CurrentDomain.BaseDirectory;
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string globalModsDir = Path.Combine(appData, "VayuClient", "Assets", "Mods");
            Directory.CreateDirectory(globalModsDir);

            var searchDirs = new List<string>
            {
                globalModsDir,
                Path.Combine(appBase, "Assets", "Mods"),
                Path.Combine(appBase, "..", "..", "..", "Assets", "Mods"),
                Path.Combine(appBase, "dist", "Assets", "Mods"),
                Path.Combine(localAppData, "Programs", "VayuClient", "Assets", "Mods"),
                appBase
            };

            string? matchedSource = null;
            VayuUiArtifactInfo? matchedInfo = null;

            // 4. Try Manifest-Driven Dynamic Resolution
            JObject? manifestDoc = await LoadOrFetchManifestAsync(searchDirs, globalModsDir);

            if (manifestDoc != null)
            {
                var artifacts = manifestDoc["artifacts"] as JArray;
                if (artifacts != null)
                {
                    var manifestCandidates = new List<(JToken art, string fileName, string sha256, string hudVer)>();

                    foreach (var art in artifacts)
                    {
                        string? fileName = art["artifactFilename"]?.ToString();
                        if (string.IsNullOrEmpty(fileName)) continue;

                        var loaders = art["supportedLoaders"]?.ToObject<List<string>>() ?? new List<string>();
                        bool loaderMatch = loaders.Count == 0 ||
                            loaders.Any(l => string.Equals(l, loader, StringComparison.OrdinalIgnoreCase)) ||
                            loaders.Any(l => string.Equals(l, "universal", StringComparison.OrdinalIgnoreCase));

                        if (!loaderMatch) continue;

                        var supportedVersions = art["supportedVersions"]?.ToObject<List<string>>() ?? new List<string>();
                        string compatibilityRange = art["minecraftCompatibilityRange"]?.ToString() ?? string.Empty;

                        bool isVersionMatch =
                            supportedVersions.Any(v => string.Equals(v, mcVersion, StringComparison.OrdinalIgnoreCase)) ||
                            (!string.IsNullOrEmpty(compatibilityRange) && IsVersionInRange(mcVersion, compatibilityRange)) ||
                            IsVersionFamilyMatch(mcVersion, supportedVersions);

                        if (!isVersionMatch) continue;

                        string hudVer = art["hudProductVersion"]?.ToString() ?? "0";
                        string sha256 = art["sha256"]?.ToString() ?? string.Empty;
                        manifestCandidates.Add((art, fileName, sha256, hudVer));
                    }

                    if (manifestCandidates.Count > 0)
                    {
                        // Sort by HUD version descending — pick best matching artifact
                        manifestCandidates.Sort((a, b) => CompareVersionStrings(b.hudVer, a.hudVer));
                        var best = manifestCandidates[0];
                        string targetFileName = best.fileName;
                        string expectedSha = best.sha256;

                        CrashLogger.LogMessage($"[VayuHUD Resolver] Best artifact candidate identified: '{targetFileName}' (HUD v{best.hudVer}) for MC {mcVersion} ({loader}).");

                        // Check local cache in searchDirs
                        string? existingLocalPath = null;
                        foreach (var dir in searchDirs)
                        {
                            if (!Directory.Exists(dir)) continue;
                            string localPath = Path.Combine(dir, targetFileName);
                            if (File.Exists(localPath))
                            {
                                if (string.IsNullOrEmpty(expectedSha) || ValidateFileSha256(localPath, expectedSha))
                                {
                                    existingLocalPath = localPath;
                                    break;
                                }
                            }
                        }

                        if (existingLocalPath != null)
                        {
                            matchedSource = existingLocalPath;
                            matchedInfo = _validator.InspectArtifact(existingLocalPath);
                            CrashLogger.LogMessage($"[VayuHUD Resolver] Found cached artifact at: {existingLocalPath}");
                        }
                        else
                        {
                            // Download on-demand from VPS
                            CrashLogger.LogMessage($"[VayuHUD Resolver] Artifact '{targetFileName}' not found locally. Downloading on demand from VPS...");
                            string? downloadedPath = await DownloadArtifactOnDemandAsync(targetFileName, expectedSha, globalModsDir);
                            if (downloadedPath != null && File.Exists(downloadedPath))
                            {
                                matchedSource = downloadedPath;
                                matchedInfo = _validator.InspectArtifact(downloadedPath);
                                CrashLogger.LogMessage($"[VayuHUD Resolver] Successfully downloaded artifact to: {downloadedPath}");
                            }
                        }
                    }
                }
            }

            // 5. Fallback: Heuristic file search in local directories
            if (matchedSource == null)
            {
                var candidatePatterns = new List<string>
                {
                    $"*mc{mcVersion}*.jar",
                    $"*mc{GetMajorMinor(mcVersion)}*.jar",
                    "*vayuclient-hud*.jar"
                };

                foreach (var pattern in candidatePatterns)
                {
                    foreach (var dir in searchDirs)
                    {
                        if (!Directory.Exists(dir)) continue;
                        var files = Directory.GetFiles(dir, pattern, SearchOption.TopDirectoryOnly);
                        foreach (var file in files)
                        {
                            if (_validator.ValidateCompatibility(jvmMajor, file, mcVersion, loader, out string failureReason))
                            {
                                matchedSource = file;
                                matchedInfo = _validator.InspectArtifact(file);
                                break;
                            }
                        }
                        if (matchedSource != null) break;
                    }
                    if (matchedSource != null) break;
                }
            }

            // 6. Graceful handling if no compatible artifact exists
            if (matchedSource == null)
            {
                CrashLogger.LogMessage($"[VayuHUD Resolver] Notice: Vayu HUD is unavailable for Minecraft {mcVersion} on loader '{loader}'. Launching native/standard Minecraft.");
                return null;
            }

            // 7. Deploy Matched Artifact cleanly
            string finalTargetName = Path.GetFileName(matchedSource);
            var deployTargets = new List<string> { modsDir };

            string gameModsDir = Path.Combine(instPath, "game", "mods");
            if (Directory.Exists(Path.Combine(instPath, "game")))
            {
                Directory.CreateDirectory(gameModsDir);
                deployTargets.Add(gameModsDir);
                CrashLogger.LogMessage($"[VayuHUD Resolver] Detected nested 'game/' directory — will also deploy to {gameModsDir}");
            }

            string? primaryDeployedPath = null;
            foreach (var deployDir in deployTargets)
            {
                try
                {
                    // Purge any older/duplicate HUD jars to prevent duplicate mixin conflicts
                    if (Directory.Exists(deployDir))
                    {
                        var existingHudJars = Directory.GetFiles(deployDir, "*vayuclient-hud*.jar", SearchOption.TopDirectoryOnly);
                        foreach (var oldJar in existingHudJars)
                        {
                            if (!string.Equals(Path.GetFileName(oldJar), finalTargetName, StringComparison.OrdinalIgnoreCase))
                            {
                                try
                                {
                                    File.Delete(oldJar);
                                    CrashLogger.LogMessage($"[VayuHUD Resolver] Cleaned up duplicate HUD JAR: {Path.GetFileName(oldJar)} from {deployDir}");
                                }
                                catch { }
                            }
                        }
                    }
                }
                catch { }

                string dest = Path.Combine(deployDir, finalTargetName);
                try
                {
                    File.Copy(matchedSource, dest, true);
                    CrashLogger.LogMessage($"[VayuHUD Resolver] Deployed '{finalTargetName}' (Bytecode {matchedInfo?.BytecodeMajor}) → {dest}");
                    primaryDeployedPath ??= dest;
                }
                catch (Exception ex)
                {
                    CrashLogger.LogMessage($"[VayuHUD Resolver] Warning: Could not deploy to {dest}: {ex.Message}");
                }
            }

            if (primaryDeployedPath == null)
            {
                CrashLogger.LogMessage("[VayuHUD Resolver] All deploy attempts failed. Launching without VayuHUD.");
                return null;
            }

            return primaryDeployedPath;
        }

        private static async Task<JObject?> LoadOrFetchManifestAsync(List<string> searchDirs, string globalModsDir)
        {
            // 1. Try local directories
            foreach (var dir in searchDirs)
            {
                if (!Directory.Exists(dir)) continue;
                string manifestPath = Path.Combine(dir, "vayu_hud_manifest.json");
                if (File.Exists(manifestPath))
                {
                    try
                    {
                        string json = await File.ReadAllTextAsync(manifestPath).ConfigureAwait(false);
                        return JObject.Parse(json);
                    }
                    catch { }
                }
            }

            // 2. Fetch from remote VPS or CDN
            var remoteUrls = new[]
            {
                $"{BackendApiClient.BaseUrl}/mods/vayu_hud_manifest.json",
                $"{BackendApiClient.FallbackUrl}/mods/vayu_hud_manifest.json",
                "https://raw.githubusercontent.com/ANSH9BOSS/VayuClient/main/VayuClient/Assets/Mods/vayu_hud_manifest.json"
            };

            foreach (var url in remoteUrls)
            {
                try
                {
                    using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(8));
                    var resp = await _httpClient.GetAsync(url, cts.Token).ConfigureAwait(false);
                    if (resp.IsSuccessStatusCode)
                    {
                        string json = await resp.Content.ReadAsStringAsync().ConfigureAwait(false);
                        var doc = JObject.Parse(json);
                        try
                        {
                            string localSave = Path.Combine(globalModsDir, "vayu_hud_manifest.json");
                            await File.WriteAllTextAsync(localSave, json).ConfigureAwait(false);
                        }
                        catch { }
                        return doc;
                    }
                }
                catch { }
            }

            return null;
        }

        private static async Task<string?> DownloadArtifactOnDemandAsync(string fileName, string expectedSha256, string targetDir)
        {
            string destFile = Path.Combine(targetDir, fileName);
            string tempFile = destFile + ".download";

            var downloadUrls = new[]
            {
                $"{BackendApiClient.BaseUrl}/mods/{fileName}",
                $"{BackendApiClient.BaseUrl}/api/v1/mods/download/{fileName}",
                $"{BackendApiClient.FallbackUrl}/mods/{fileName}",
                $"https://raw.githubusercontent.com/ANSH9BOSS/VayuClient/main/VayuClient/Assets/Mods/{fileName}"
            };

            foreach (var url in downloadUrls)
            {
                try
                {
                    CrashLogger.LogMessage($"[VayuHUD Resolver] Attempting download from: {url}");
                    using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(45));
                    using var resp = await _httpClient.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cts.Token).ConfigureAwait(false);

                    if (!resp.IsSuccessStatusCode)
                    {
                        CrashLogger.LogMessage($"[VayuHUD Resolver] Download failed HTTP {(int)resp.StatusCode} for {url}");
                        continue;
                    }

                    if (File.Exists(tempFile)) File.Delete(tempFile);

                    using (var stream = await resp.Content.ReadAsStreamAsync().ConfigureAwait(false))
                    using (var fs = new FileStream(tempFile, FileMode.Create, FileAccess.Write, FileShare.None))
                    {
                        await stream.CopyToAsync(fs, cts.Token).ConfigureAwait(false);
                    }

                    if (!string.IsNullOrEmpty(expectedSha256) && !ValidateFileSha256(tempFile, expectedSha256))
                    {
                        CrashLogger.LogMessage($"[VayuHUD Resolver] SHA256 checksum mismatch for downloaded file: {fileName}");
                        if (File.Exists(tempFile)) File.Delete(tempFile);
                        continue;
                    }

                    if (File.Exists(destFile)) File.Delete(destFile);
                    File.Move(tempFile, destFile);
                    return destFile;
                }
                catch (Exception ex)
                {
                    CrashLogger.LogMessage($"[VayuHUD Resolver] Error downloading from {url}: {ex.Message}");
                    if (File.Exists(tempFile))
                    {
                        try { File.Delete(tempFile); } catch { }
                    }
                }
            }

            return null;
        }

        private static bool ValidateFileSha256(string filePath, string expectedSha256)
        {
            try
            {
                using var sha = SHA256.Create();
                using var stream = File.OpenRead(filePath);
                var hash = sha.ComputeHash(stream);
                string computed = BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
                return string.Equals(computed, expectedSha256, StringComparison.OrdinalIgnoreCase);
            }
            catch
            {
                return false;
            }
        }

        private static bool IsVersionInRange(string version, string rangeSpec)
        {
            if (string.IsNullOrWhiteSpace(version) || string.IsNullOrWhiteSpace(rangeSpec)) return false;

            try
            {
                var tokens = rangeSpec.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
                foreach (var token in tokens)
                {
                    if (token.StartsWith(">="))
                    {
                        var minVer = token.Substring(2).Trim();
                        if (CompareVersions(version, minVer) < 0) return false;
                    }
                    else if (token.StartsWith("<="))
                    {
                        var maxVer = token.Substring(2).Trim();
                        if (CompareVersions(version, maxVer) > 0) return false;
                    }
                    else if (token.StartsWith("="))
                    {
                        var exact = token.Substring(1).Trim();
                        if (!string.Equals(version, exact, StringComparison.OrdinalIgnoreCase)) return false;
                    }
                }
                return true;
            }
            catch
            {
                return false;
            }
        }

        private static int CompareVersions(string v1, string v2)
        {
            var p1 = v1.Split('.').Select(s => int.TryParse(s, out int n) ? n : 0).ToArray();
            var p2 = v2.Split('.').Select(s => int.TryParse(s, out int n) ? n : 0).ToArray();

            int len = Math.Max(p1.Length, p2.Length);
            for (int i = 0; i < len; i++)
            {
                int num1 = i < p1.Length ? p1[i] : 0;
                int num2 = i < p2.Length ? p2[i] : 0;
                if (num1 != num2) return num1.CompareTo(num2);
            }
            return 0;
        }

        private static string GetMajorMinor(string version)
        {
            if (string.IsNullOrWhiteSpace(version)) return version;
            var parts = version.Split('.');
            if (parts.Length >= 2)
            {
                return $"{parts[0]}.{parts[1]}";
            }
            return version;
        }

        private static bool IsVersionFamilyMatch(string mcVersion, IEnumerable<string> supportedVersions)
        {
            if (string.IsNullOrEmpty(mcVersion)) return false;
            var major = GetMajorMinor(mcVersion);
            foreach (var sv in supportedVersions)
            {
                if (mcVersion.StartsWith(sv + ".", StringComparison.OrdinalIgnoreCase)) return true;
                if (string.Equals(sv, mcVersion, StringComparison.OrdinalIgnoreCase)) return true;
                if (!string.IsNullOrEmpty(major) && sv.StartsWith(major, StringComparison.OrdinalIgnoreCase)) return true;
            }
            return false;
        }

        private static int CompareVersionStrings(string v1, string v2)
        {
            var p1 = (v1 ?? "0").Split('.').Select(s => int.TryParse(s, out int n) ? n : 0).ToArray();
            var p2 = (v2 ?? "0").Split('.').Select(s => int.TryParse(s, out int n) ? n : 0).ToArray();
            int len = Math.Max(p1.Length, p2.Length);
            for (int i = 0; i < len; i++)
            {
                int n1 = i < p1.Length ? p1[i] : 0;
                int n2 = i < p2.Length ? p2[i] : 0;
                if (n1 != n2) return n1.CompareTo(n2);
            }
            return 0;
        }
    }
}
