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

            var tokenMap = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
            {
                { "natives_directory", parameters.InstanceNativesDir },
                { "launcher_name", "VayuClient" },
                { "launcher_version", Core.AppInfo.VersionString },
                { "classpath", string.Join(";", parameters.Classpath) },
                { "classpath_separator", ";" },
                { "library_directory", librariesDir },
                { "auth_player_name", parameters.Profile.Username },
                { "auth_session", string.IsNullOrEmpty(parameters.Profile.AccessToken) ? "token:0:0" : parameters.Profile.AccessToken },
                { "user_properties", "{}" },
                { "auth_xuid", parameters.Profile.Uuid },
                { "clientid", Guid.NewGuid().ToString("N") },
                { "quickPlayPath", "" },
                { "quickPlaySingleplayer", "" },
                { "quickPlayMultiplayer", "" },
                { "quickPlayRealms", "" },
                { "version_name", parameters.VersionPackage.Id },
                { "game_directory", parameters.Instance.GameDirectory },
                { "assets_root", parameters.SharedAssetsDir },
                { "assets_index_name", parameters.VersionPackage.AssetIndex?.Id ?? parameters.VersionPackage.Assets ?? "legacy" },
                { "auth_uuid", parameters.Profile.Uuid },
                { "auth_access_token", string.IsNullOrEmpty(parameters.Profile.AccessToken) ? "0" : parameters.Profile.AccessToken },
                { "user_type", parameters.Profile.AccountType == AccountType.Microsoft ? "msa" : "offline" },
                { "version_type", parameters.VersionPackage.Type ?? "release" },
                { "resolution_width", "1280" },
                { "resolution_height", "720" }
            };

            // 1. JVM Arguments
            var jvmArgs = new List<string>();

            // Memory
            int ramMB = Math.Max(1024, parameters.Instance.RamMB);
            int minRamMB = Math.Min(1024, ramMB / 2);
            jvmArgs.Add($"-Xms{minRamMB}M");
            jvmArgs.Add($"-Xmx{ramMB}M");

            // Standard JVM properties
            if (parameters.VersionPackage.Arguments?.Jvm == null || parameters.VersionPackage.Arguments.Jvm.Count == 0)
            {
                jvmArgs.Add($"-Djava.library.path={parameters.InstanceNativesDir}");
                jvmArgs.Add($"-Djna.tmpdir={parameters.InstanceNativesDir}");
                jvmArgs.Add($"-Dorg.lwjgl.system.SharedLibraryExtractPath={parameters.InstanceNativesDir}");
            }
            jvmArgs.Add("-Dminecraft.launcher.brand=VayuClient");
            jvmArgs.Add($"-Dminecraft.launcher.version={Core.AppInfo.VersionString}");

            // Hardware-optimized thread allocation & 2D/3D hardware pipeline flags
            int logicalCores = Math.Max(2, Environment.ProcessorCount);
            int parallelGcThreads = Math.Max(2, logicalCores > 8 ? logicalCores / 2 : logicalCores - 1);
            int concGcThreads = Math.Max(1, parallelGcThreads / 2);

            jvmArgs.Add($"-XX:ParallelGCThreads={parallelGcThreads}");
            jvmArgs.Add($"-XX:ConcGCThreads={concGcThreads}");
            jvmArgs.Add("-Dsun.java2d.d3d=true");
            jvmArgs.Add("-Dsun.java2d.noddraw=true");
            jvmArgs.Add("-Dsun.awt.noerasebackground=true");

            // Check if arguments.jvm defined in version JSON
            if (parameters.VersionPackage.Arguments?.Jvm != null && parameters.VersionPackage.Arguments.Jvm.Count > 0)
            {
                foreach (var jvmItem in parameters.VersionPackage.Arguments.Jvm)
                {
                    if (jvmItem is string strVal)
                    {
                        var resolved = ReplaceTokens(strVal, tokenMap);
                        if (!string.IsNullOrWhiteSpace(resolved)) jvmArgs.Add(resolved);
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
                                        var resolved = ReplaceTokens(sub.ToString(), tokenMap);
                                        if (!string.IsNullOrWhiteSpace(resolved)) jvmArgs.Add(resolved);
                                    }
                                }
                            }
                            else if (val != null)
                            {
                                var resolved = ReplaceTokens(val.ToString(), tokenMap);
                                if (!string.IsNullOrWhiteSpace(resolved)) jvmArgs.Add(resolved);
                            }
                        }
                    }
                }
            }

            // Add user additional JVM args if any
            if (parameters.AdditionalJvmArgs != null)
            {
                jvmArgs.AddRange(parameters.AdditionalJvmArgs);
            }

            // Classpath
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
                // Legacy minecraftArguments string format
                var legacyTokens = parameters.VersionPackage.MinecraftArguments.Split(' ', StringSplitOptions.RemoveEmptyEntries);
                foreach (var token in legacyTokens)
                {
                    var resolved = ReplaceTokens(token, tokenMap);
                    if (!string.IsNullOrWhiteSpace(resolved)) gameArgs.Add(resolved);
                }
            }
            else
            {
                // Baseline fallback arguments
                gameArgs.AddRange(new[]
                {
                    "--username", parameters.Profile.Username,
                    "--version", parameters.VersionPackage.Id,
                    "--gameDir", parameters.Instance.GameDirectory,
                    "--assetsDir", parameters.SharedAssetsDir,
                    "--assetIndex", parameters.VersionPackage.AssetIndex?.Id ?? "legacy",
                    "--uuid", parameters.Profile.Uuid,
                    "--accessToken", string.IsNullOrEmpty(parameters.Profile.AccessToken) ? "0" : parameters.Profile.AccessToken,
                    "--userType", parameters.Profile.AccountType == AccountType.Microsoft ? "msa" : "offline",
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

                // 1. OS check
                var os = rule["os"]?["name"]?.ToString();
                if (os != null && !string.Equals(os, "windows", StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                // 2. Features check (e.g. quick play, custom resolution, demo user)
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
                                // Never allow quick play flags unless explicitly requested in parameters
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

        private static string Quote(string val)
        {
            if (string.IsNullOrEmpty(val)) return string.Empty;
            return val.Contains(' ') && !val.StartsWith('"') ? $"\"{val}\"" : val;
        }
    }
}
