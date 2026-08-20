using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Java;

namespace VayuClient.Services.Launch
{
    public class VayuUIArtifactResolver : IVayuUIArtifactResolver
    {
        private readonly IVayuUiCompatibilityValidator _validator;

        public VayuUIArtifactResolver(IVayuUiCompatibilityValidator? validator = null)
        {
            _validator = validator ?? new VayuUiCompatibilityValidator();
        }

        public async Task<string?> ResolveAndDeployAsync(MinecraftInstance instance, JavaRuntimeInfo javaRuntime)
        {
            await Task.Yield();
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
            var searchDirs = new List<string>
            {
                Path.Combine(appBase, "Assets", "Mods"),
                Path.Combine(appBase, "..", "..", "..", "Assets", "Mods"),
                Path.Combine(appBase, "dist", "Assets", "Mods"),
                Path.Combine(localAppData, "Programs", "VayuClient", "Assets", "Mods"),
                Path.Combine(appData, "VayuClient", "Assets", "Mods"),
                appBase
            };

            string? matchedSource = null;
            VayuUiArtifactInfo? matchedInfo = null;

            // 4. Try Manifest-Driven Dynamic Resolution
            // Collect all candidates, then pick the highest HUD version that matches
            var manifestCandidates = new List<(string path, VayuUiArtifactInfo? info, string hudVer)>();

            foreach (var dir in searchDirs)
            {
                if (!Directory.Exists(dir)) continue;
                string manifestPath = Path.Combine(dir, "vayu_hud_manifest.json");
                if (!File.Exists(manifestPath)) continue;

                try
                {
                    var json = File.ReadAllText(manifestPath);
                    var doc = JObject.Parse(json);
                    var artifacts = doc["artifacts"] as JArray;
                    if (artifacts == null) continue;

                    foreach (var art in artifacts)
                    {
                        string? fileName = art["artifactFilename"]?.ToString();
                        if (string.IsNullOrEmpty(fileName)) continue;

                        string candidatePath = Path.Combine(dir, fileName);
                        if (!File.Exists(candidatePath)) continue;

                        // Check supported loader (with fallback to universal)
                        var loaders = art["supportedLoaders"]?.ToObject<List<string>>() ?? new List<string>();
                        bool loaderMatch = loaders.Count == 0 ||
                            loaders.Any(l => string.Equals(l, loader, StringComparison.OrdinalIgnoreCase)) ||
                            loaders.Any(l => string.Equals(l, "universal", StringComparison.OrdinalIgnoreCase));

                        if (!loaderMatch) continue;

                        // Check Minecraft version — exact list, range, or prefix match
                        var supportedVersions = art["supportedVersions"]?.ToObject<List<string>>() ?? new List<string>();
                        string compatibilityRange = art["minecraftCompatibilityRange"]?.ToString() ?? string.Empty;

                        bool isVersionMatch =
                            supportedVersions.Any(v => string.Equals(v, mcVersion, StringComparison.OrdinalIgnoreCase)) ||
                            (!string.IsNullOrEmpty(compatibilityRange) && IsVersionInRange(mcVersion, compatibilityRange)) ||
                            IsVersionFamilyMatch(mcVersion, supportedVersions); // prefix fallback

                        if (!isVersionMatch) continue;

                        if (_validator.ValidateCompatibility(jvmMajor, candidatePath, mcVersion, loader, out string failureReason))
                        {
                            string hudVer = art["hudProductVersion"]?.ToString() ?? "0";
                            manifestCandidates.Add((candidatePath, _validator.InspectArtifact(candidatePath), hudVer));
                            CrashLogger.LogMessage($"[VayuHUD Resolver] Candidate found: '{fileName}' (HUD v{hudVer}) for MC {mcVersion} ({loader}).");
                        }
                        else
                        {
                            CrashLogger.LogMessage($"[VayuHUD Resolver] Candidate '{fileName}' failed validation: {failureReason}");
                        }
                    }
                }
                catch (Exception ex)
                {
                    CrashLogger.LogMessage($"[VayuHUD Resolver] Error reading manifest at {manifestPath}: {ex.Message}");
                }

                if (manifestCandidates.Count > 0) break; // stop at first searchDir that has matches
            }

            // Sort by HUD version descending — always deploy the newest
            if (manifestCandidates.Count > 0)
            {
                manifestCandidates.Sort((a, b) => CompareVersionStrings(b.hudVer, a.hudVer));
                var best = manifestCandidates[0];
                matchedSource = best.path;
                matchedInfo = best.info;
                CrashLogger.LogMessage($"[VayuHUD Resolver] Selected best artifact '{Path.GetFileName(matchedSource)}' (HUD v{best.hudVer}) for Minecraft {mcVersion} ({loader}).");
            }

            // 5. Fallback: Heuristic file search
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
            string targetFileName = Path.GetFileName(matchedSource);
            string targetJar = Path.Combine(modsDir, targetFileName);

            // Determine all mods directories to deploy into (handles nested game/ structure)
            var deployTargets = new List<string> { modsDir };

            // Also deploy to game/mods/ if that structure exists (VayuClient nested instance layout)
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
                    // Purge any existing or outdated HUD jars in the target folder to prevent duplicates
                    if (Directory.Exists(deployDir))
                    {
                        var existingHudJars = Directory.GetFiles(deployDir, "*vayuclient-hud*.jar", SearchOption.TopDirectoryOnly);
                        foreach (var oldJar in existingHudJars)
                        {
                            if (!string.Equals(Path.GetFileName(oldJar), targetFileName, StringComparison.OrdinalIgnoreCase))
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

                string dest = Path.Combine(deployDir, targetFileName);
                try
                {
                    File.Copy(matchedSource, dest, true);
                    CrashLogger.LogMessage($"[VayuHUD Resolver] Deployed '{targetFileName}' (Bytecode {matchedInfo?.BytecodeMajor}) → {dest}");
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

        private static bool IsVersionInRange(string version, string rangeSpec)
        {
            if (string.IsNullOrWhiteSpace(version) || string.IsNullOrWhiteSpace(rangeSpec)) return false;

            try
            {
                // Handles specs like ">=1.21 <=1.21.11" or "=1.21.4" or ">=26.1 <=26.2"
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

        /// <summary>
        /// Prefix/family match: e.g. "1.21.11" matches "1.21" or "1.21.x" entries.
        /// Also handles 26.x family.
        /// </summary>
        private static bool IsVersionFamilyMatch(string mcVersion, IEnumerable<string> supportedVersions)
        {
            if (string.IsNullOrEmpty(mcVersion)) return false;
            var major = GetMajorMinor(mcVersion);
            foreach (var sv in supportedVersions)
            {
                // Direct major.minor match (e.g. "1.21" matches "1.21.11")
                if (mcVersion.StartsWith(sv + ".", StringComparison.OrdinalIgnoreCase)) return true;
                if (string.Equals(sv, mcVersion, StringComparison.OrdinalIgnoreCase)) return true;
                if (!string.IsNullOrEmpty(major) && sv.StartsWith(major, StringComparison.OrdinalIgnoreCase)) return true;
            }
            return false;
        }

        /// <summary>Compares two version strings like "1.9.1" vs "1.9.0". Returns negative if v1 &lt; v2.</summary>
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
