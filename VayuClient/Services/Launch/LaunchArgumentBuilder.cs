using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Newtonsoft.Json.Linq;
using VayuClient.Models;

namespace VayuClient.Services.Launch
{
    public class LaunchArgumentBuilder : ILaunchArgumentBuilder
    {
        public LaunchArgumentsResult BuildArguments(LaunchParameters parameters)
        {
            var result = new LaunchArgumentsResult
            {
                JavaExecutablePath = parameters.JavaRuntime.Path,
                MainClass = parameters.CustomMainClass ?? parameters.VersionPackage.MainClass ?? "net.minecraft.client.main.Main"
            };

            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var librariesDir = Path.Combine(appData, "VayuClient", "libraries");

            bool isMsa = parameters.Profile.AccountType == AccountType.Microsoft && !string.IsNullOrWhiteSpace(parameters.Profile.AccessToken);
            string accessToken = isMsa ? parameters.Profile.AccessToken! : "0";
            string userType = isMsa ? "msa" : "mojang";
            string xuid = isMsa ? (parameters.Profile.Uuid ?? string.Empty) : string.Empty;

            var tokenMap = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
            {
                { "natives_directory", parameters.InstanceNativesDir },
                { "launcher_name", "VayuClient" },
                { "launcher_version", Core.AppInfo.VersionString },
                { "classpath", string.Join(";", parameters.Classpath) },
                { "classpath_separator", ";" },
                { "library_directory", librariesDir },
                { "auth_player_name", parameters.Profile.Username },
                { "auth_session", accessToken },
                { "user_properties", "{}" },
                { "auth_xuid", xuid },
                { "clientid", Guid.NewGuid().ToString("N") },
                { "quickPlayPath", "" },
                { "quickPlaySingleplayer", "" },
                { "quickPlayMultiplayer", "" },
                { "quickPlayRealms", "" },
                { "version_name", parameters.VersionPackage.Id },
                { "game_directory", parameters.Instance.GameDirectory },
                { "assets_root", parameters.SharedAssetsDir },
                { "assets_index_name", parameters.VersionPackage.AssetIndex?.Id ?? parameters.VersionPackage.Assets ?? "legacy" },
                { "auth_uuid", parameters.Profile.Uuid ?? string.Empty },
                { "auth_access_token", accessToken },
                { "user_type", userType },
                { "version_type", parameters.VersionPackage.Type ?? "release" },
                { "resolution_width", "1280" },
                { "resolution_height", "720" }
            };

            // 1. JVM Arguments
            var jvmArgs = new List<string>();

            // ─── MEMORY CONFIGURATION ─────────────────────────────────────────
            // Setting Xms equal to Xmx avoids dynamic heap resizing during chunk generation
            int ramMB = Math.Max(1024, parameters.Instance.RamMB);

            bool hasCustomXms = parameters.AdditionalJvmArgs?.Any(a => a.StartsWith("-Xms", StringComparison.OrdinalIgnoreCase)) == true;
            bool hasCustomXmx = parameters.AdditionalJvmArgs?.Any(a => a.StartsWith("-Xmx", StringComparison.OrdinalIgnoreCase)) == true;

            if (!hasCustomXms)
            {
                jvmArgs.Add($"-Xms{ramMB}M");
            }
            if (!hasCustomXmx)
            {
                jvmArgs.Add($"-Xmx{ramMB}M");
            }

            // ─── CLIENT HIGH-FPS G1GC & SHADER TUNING (Optimized for Ultra-High FPS & Zero Stutter) ───
            // Tuned for client rendering (Sodium/Iris/Fabric/Vanilla/Shaders)
            jvmArgs.Add("-XX:+UseG1GC");
            jvmArgs.Add("-XX:+ParallelRefProcEnabled");
            jvmArgs.Add("-XX:MaxGCPauseMillis=10");
            jvmArgs.Add("-XX:+UnlockExperimentalVMOptions");
            jvmArgs.Add("-XX:+AlwaysPreTouch");
            jvmArgs.Add("-XX:G1NewSizePercent=30");
            jvmArgs.Add("-XX:G1MaxNewSizePercent=50");
            jvmArgs.Add("-XX:G1ReservePercent=15");
            jvmArgs.Add("-XX:G1HeapRegionSize=32M");
            jvmArgs.Add("-XX:G1MixedGCCountTarget=4");
            jvmArgs.Add("-XX:InitiatingHeapOccupancyPercent=15");
            jvmArgs.Add("-XX:G1MixedGCLiveThresholdPercent=90");
            jvmArgs.Add("-XX:G1RSetUpdatingPauseTimePercent=5");
            jvmArgs.Add("-XX:SurvivorRatio=32");
            jvmArgs.Add("-XX:+PerfDisableSharedMem");
            jvmArgs.Add("-XX:+UseStringDeduplication");
            jvmArgs.Add("-XX:ReservedCodeCacheSize=512M");
            jvmArgs.Add("-XX:InitialCodeCacheSize=128M");

            // Allocate direct memory for Iris/Sodium shader vertex and shadow buffers
            int directMemoryMB = Math.Max(4096, ramMB);
            jvmArgs.Add($"-XX:MaxDirectMemorySize={directMemoryMB}M");

            // Hardware-optimized GC thread allocation
            int logicalCores = Math.Max(2, Environment.ProcessorCount);
            int parallelGcThreads = Math.Max(2, logicalCores > 8 ? logicalCores / 2 : logicalCores - 1);
            int concGcThreads = Math.Max(1, parallelGcThreads / 2);

            jvmArgs.Add($"-XX:ParallelGCThreads={parallelGcThreads}");
            jvmArgs.Add($"-XX:ConcGCThreads={concGcThreads}");

            // Standard JVM properties & native allocator boost
            if (parameters.VersionPackage.Arguments?.Jvm == null || parameters.VersionPackage.Arguments.Jvm.Count == 0)
            {
                jvmArgs.Add($"-Djava.library.path={parameters.InstanceNativesDir}");
                jvmArgs.Add($"-Djna.tmpdir={parameters.InstanceNativesDir}");
                jvmArgs.Add($"-Dorg.lwjgl.system.SharedLibraryExtractPath={parameters.InstanceNativesDir}");
            }
            jvmArgs.Add("-Dorg.lwjgl.system.allocator=system");
            jvmArgs.Add("-Dsun.java2d.noddraw=true");
            jvmArgs.Add("-Dorg.lwjgl.opengl.Display.enableHighDPI=true");
            jvmArgs.Add("-Dminecraft.launcher.brand=VayuClient");
            jvmArgs.Add($"-Dminecraft.launcher.version={Core.AppInfo.VersionString}");

            int javaMajor = parameters.JavaRuntime?.MajorVersion ?? 21;

            // Version-specific JVM arguments from package
            if (parameters.VersionPackage.Arguments?.Jvm != null && parameters.VersionPackage.Arguments.Jvm.Count > 0)
            {
                foreach (var jvmItem in parameters.VersionPackage.Arguments.Jvm)
                {
                    if (jvmItem is string strVal)
                    {
                        AddJvmArgumentIfCompatible(strVal, tokenMap, jvmArgs, javaMajor);
                    }
                    else if (jvmItem is JObject jObj)
                    {
                        if (IsArgumentAllowed(jObj, parameters))
                        {
                            var val = jObj["value"];
                            if (val is JArray arr)
                            {
                                foreach (var sub in arr)
                                {
                                    if (sub != null)
                                    {
                                        AddJvmArgumentIfCompatible(sub.ToString(), tokenMap, jvmArgs, javaMajor);
                                    }
                                }
                            }
                            else if (val != null)
                            {
                                AddJvmArgumentIfCompatible(val.ToString(), tokenMap, jvmArgs, javaMajor);
                            }
                        }
                    }
                }
            }

            // User additional JVM args (override defaults)
            if (parameters.AdditionalJvmArgs != null)
            {
                foreach (var arg in parameters.AdditionalJvmArgs)
                {
                    if (string.IsNullOrWhiteSpace(arg)) continue;
                    var trimmed = arg.Trim();
                    if (trimmed == "-cp" || trimmed == "-classpath") continue;
                    if (!jvmArgs.Contains(trimmed, StringComparer.OrdinalIgnoreCase))
                    {
                        jvmArgs.Add(trimmed);
                    }
                }
            }

            // Classpath is placed exactly once at the end of JVM flags
            jvmArgs.Add("-cp");
            jvmArgs.Add(string.Join(";", parameters.Classpath));

            result.JvmArguments = jvmArgs;

            // 2. Game Arguments
            var gameArgs = new List<string>();

            if (parameters.VersionPackage.Arguments?.Game != null && parameters.VersionPackage.Arguments.Game.Count > 0)
            {
                foreach (var gameItem in parameters.VersionPackage.Arguments.Game)
                {
                    if (gameItem is string strVal)
                    {
                        var resolved = ReplaceTokens(strVal, tokenMap);
                        if (!string.IsNullOrWhiteSpace(resolved)) gameArgs.Add(resolved);
                    }
                    else if (gameItem is JObject jObj)
                    {
                        if (IsArgumentAllowed(jObj, parameters))
                        {
                            var val = jObj["value"];
                            if (val is JArray arr)
                            {
                                foreach (var sub in arr)
                                {
                                    if (sub != null)
                                    {
                                        var resolved = ReplaceTokens(sub.ToString(), tokenMap);
                                        if (!string.IsNullOrWhiteSpace(resolved)) gameArgs.Add(resolved);
                                    }
                                }
                            }
                            else if (val != null)
                            {
                                var resolved = ReplaceTokens(val.ToString(), tokenMap);
                                if (!string.IsNullOrWhiteSpace(resolved)) gameArgs.Add(resolved);
                            }
                        }
                    }
                }
            }
            else if (!string.IsNullOrEmpty(parameters.VersionPackage.MinecraftArguments))
            {
                var legacyTokens = parameters.VersionPackage.MinecraftArguments.Split(' ', StringSplitOptions.RemoveEmptyEntries);
                foreach (var token in legacyTokens)
                {
                    var resolved = ReplaceTokens(token, tokenMap);
                    if (!string.IsNullOrWhiteSpace(resolved)) gameArgs.Add(resolved);
                }
            }
            else
            {
                gameArgs.AddRange(new[]
                {
                    "--username", parameters.Profile.Username,
                    "--version", parameters.VersionPackage.Id,
                    "--gameDir", parameters.Instance.GameDirectory,
                    "--assetsDir", parameters.SharedAssetsDir,
                    "--assetIndex", parameters.VersionPackage.AssetIndex?.Id ?? "legacy",
                    "--uuid", parameters.Profile.Uuid,
                    "--accessToken", accessToken,
                    "--userType", userType,
                    "--versionType", parameters.VersionPackage.Type ?? "release"
                });
            }

            if (parameters.AdditionalGameArgs != null)
            {
                gameArgs.AddRange(parameters.AdditionalGameArgs);
            }

            result.GameArguments = gameArgs;
            return result;
        }

        private static bool IsArgumentAllowed(JObject obj, LaunchParameters parameters)
        {
            var rules = obj["rules"] as JArray;
            if (rules == null || rules.Count == 0) return true;

            bool allowed = false;
            foreach (var rule in rules)
            {
                var action = rule["action"]?.ToString() ?? "allow";
                bool isAllow = string.Equals(action, "allow", StringComparison.OrdinalIgnoreCase);

                var os = rule["os"]?["name"]?.ToString();
                if (os != null && !string.Equals(os, "windows", StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                if (rule["features"] is JObject features)
                {
                    bool featuresMatch = true;
                    foreach (var prop in features.Properties())
                    {
                        switch (prop.Name)
                        {
                            case "is_demo_user":
                                if (prop.Value.Value<bool>() != parameters.IsDemo) featuresMatch = false;
                                break;
                            case "has_custom_resolution":
                                if (prop.Value.Value<bool>() != (parameters.WindowWidth > 0 && parameters.WindowHeight > 0)) featuresMatch = false;
                                break;
                            case "is_quick_play_path":
                            case "is_quick_play_singleplayer":
                            case "is_quick_play_multiplayer":
                            case "is_quick_play_realms":
                                featuresMatch = false;
                                break;
                            default:
                                featuresMatch = false;
                                break;
                        }
                    }

                    if (!featuresMatch)
                    {
                        continue;
                    }
                }

                allowed = isAllow;
            }

            return allowed;
        }

        private static string ReplaceTokens(string template, Dictionary<string, string> tokenMap)
        {
            return Regex.Replace(template, @"\$\{([^}]+)\}", match =>
            {
                var key = match.Groups[1].Value;
                return tokenMap.TryGetValue(key, out var val) ? (val ?? string.Empty) : match.Value;
            });
        }

        private static void AddJvmArgumentIfCompatible(string rawArg, Dictionary<string, string> tokenMap, List<string> jvmArgs, int javaMajor)
        {
            if (string.IsNullOrWhiteSpace(rawArg)) return;
            var trimmed = rawArg.Trim();

            if (trimmed == "-cp" || trimmed == "-classpath" || trimmed.Contains("${classpath}"))
            {
                return;
            }

            if (trimmed.StartsWith("--sun-misc-unsafe-memory-access", StringComparison.OrdinalIgnoreCase) && javaMajor < 24)
            {
                return;
            }

            var resolved = ReplaceTokens(trimmed, tokenMap);
            if (!string.IsNullOrWhiteSpace(resolved) && !jvmArgs.Contains(resolved, StringComparer.OrdinalIgnoreCase))
            {
                jvmArgs.Add(resolved);
            }
        }
    }
}
