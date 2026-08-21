using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Services.Java;

namespace VayuClient.Services.Launch
{
    public class VayuUiCompatibilityValidator : IVayuUiCompatibilityValidator
    {
        public VayuUiArtifactInfo InspectArtifact(string jarPath)
        {
            var info = new VayuUiArtifactInfo
            {
                FilePath = jarPath
            };

            if (!File.Exists(jarPath))
            {
                info.IsValid = false;
                info.ErrorMessage = $"HUD JAR file not found at: {jarPath}";
                return info;
            }

            try
            {
                using var zip = ZipFile.OpenRead(jarPath);

                // 1. Check for fabric.mod.json
                var fabricEntry = zip.GetEntry("fabric.mod.json");
                if (fabricEntry != null)
                {
                    try
                    {
                        using var reader = new StreamReader(fabricEntry.Open());
                        var json = reader.ReadToEnd();
                        var obj = JObject.Parse(json);

                        info.VayuUiVersion = obj["version"]?.ToString() ?? "1.9.3";
                        info.MinecraftCompatibility = obj["depends"]?["minecraft"]?.ToString() ?? string.Empty;
                        info.SupportedLoaders.Add("Fabric");
                        info.SupportedLoaders.Add("Quilt");
                        info.HasManifest = true;
                    }
                    catch { }
                }

                // 2. Check for Forge / NeoForge manifests
                var forgeEntry = zip.GetEntry("META-INF/mods.toml") ?? zip.GetEntry("mcmod.info");
                if (forgeEntry != null)
                {
                    info.SupportedLoaders.Add("Forge");
                    info.SupportedLoaders.Add("NeoForge");
                    info.HasManifest = true;
                }

                var manifestEntry = zip.GetEntry("vayuclient-hud-manifest.json") ?? zip.GetEntry("vayuclient-ui-manifest.json");
                if (manifestEntry != null)
                {
                    info.HasManifest = true;
                    try
                    {
                        using var reader = new StreamReader(manifestEntry.Open());
                        var json = reader.ReadToEnd();
                        var obj = JObject.Parse(json);

                        info.VayuUiVersion = obj["vayuUiVersion"]?.ToString() ?? obj["version"]?.ToString() ?? info.VayuUiVersion;
                        info.MinecraftCompatibility = obj["minecraftCompatibility"]?.ToString() ?? info.MinecraftCompatibility;
                        info.RequiredJavaMajor = obj["requiredJavaMajor"]?.Value<int>() ?? 0;
                        info.BytecodeMajor = obj["bytecodeMajor"]?.Value<int>() ?? 0;

                        var loaders = obj["supportedLoaders"] as JArray;
                        if (loaders != null)
                        {
                            foreach (var l in loaders)
                            {
                                var lStr = l.ToString();
                                if (!info.SupportedLoaders.Contains(lStr, StringComparer.OrdinalIgnoreCase))
                                {
                                    info.SupportedLoaders.Add(lStr);
                                }
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        CrashLogger.LogMessage($"[VayuHUD] Warning: could not parse manifest in {jarPath}: {ex.Message}");
                    }
                }

                // 3. Direct binary bytecode inspection on entrypoint class
                var classEntry = zip.GetEntry("com/vayuclient/hud/VayuHUDClient.class")
                              ?? zip.GetEntry("com/vayuclient/hud/VayuHUD.class")
                              ?? zip.GetEntry("com/vayuclient/ui/VayuClientUI.class")
                              ?? zip.Entries.FirstOrDefault(e => e.FullName.EndsWith(".class", StringComparison.OrdinalIgnoreCase));

                if (classEntry != null)
                {
                    using var stream = classEntry.Open();
                    using var ms = new MemoryStream();
                    stream.CopyTo(ms);
                    var bytes = ms.ToArray();

                    if (bytes.Length >= 8)
                    {
                        // Check CAFEBABE magic
                        if (bytes[0] == 0xCA && bytes[1] == 0xFE && bytes[2] == 0xBA && bytes[3] == 0xBE)
                        {
                            int minor = (bytes[4] << 8) | bytes[5];
                            int major = (bytes[6] << 8) | bytes[7];

                            info.BytecodeMinor = minor;
                            info.BytecodeMajor = major;

                            if (info.RequiredJavaMajor <= 0)
                            {
                                info.RequiredJavaMajor = (major >= 45) ? (major - 44) : 8;
                            }

                            info.IsValid = true;
                        }
                        else
                        {
                            info.IsValid = false;
                            info.ErrorMessage = "Invalid Java class magic number (expected CAFEBABE).";
                        }
                    }
                    else
                    {
                        info.IsValid = false;
                        info.ErrorMessage = "Class file length is less than 8 bytes.";
                    }
                }
                else
                {
                    info.IsValid = false;
                    info.ErrorMessage = "No .class file found in JAR archive.";
                }
            }
            catch (Exception ex)
            {
                info.IsValid = false;
                info.ErrorMessage = $"Failed to inspect JAR archive: {ex.Message}";
            }

            return info;
        }

        public bool ValidateCompatibility(int jvmJavaMajor, string jarPath, string minecraftVersion, out string failureReason)
        {
            return ValidateCompatibility(jvmJavaMajor, jarPath, minecraftVersion, "Fabric", out failureReason);
        }

        public bool ValidateCompatibility(int jvmJavaMajor, string jarPath, string minecraftVersion, string loader, out string failureReason)
        {
            failureReason = string.Empty;
            var info = InspectArtifact(jarPath);

            if (!info.IsValid)
            {
                failureReason = $"VayuClient HUD artifact is corrupted or invalid: {info.ErrorMessage}";
                return false;
            }

            // 1. JVM Java Bytecode Version Check
            int jvmMaxBytecode = JavaRuntimeService.GetMaxClassFileVersion(jvmJavaMajor);
            if (info.BytecodeMajor > jvmMaxBytecode)
            {
                int uiRequiredJava = info.RequiredJavaMajor > 0 ? info.RequiredJavaMajor : (info.BytecodeMajor - 44);
                failureReason = $"VayuClient HUD requires Java {uiRequiredJava} (bytecode {info.BytecodeMajor}), but Minecraft is running on Java {jvmJavaMajor} (max bytecode {jvmMaxBytecode}).";
                return false;
            }

            // 2. Mod Loader Compatibility Check
            if (!string.IsNullOrEmpty(loader) && info.SupportedLoaders.Count > 0)
            {
                bool loaderSupported = info.SupportedLoaders.Any(l => string.Equals(l, loader, StringComparison.OrdinalIgnoreCase));
                if (!loaderSupported)
                {
                    failureReason = $"VayuClient HUD artifact at {Path.GetFileName(jarPath)} only supports ({string.Join(", ", info.SupportedLoaders)}), but instance loader is '{loader}'.";
                    return false;
                }
            }

            // 3. Minecraft Version Compatibility Check
            if (!IsMinecraftVersionCompatible(minecraftVersion, info.MinecraftCompatibility, Path.GetFileName(jarPath)))
            {
                failureReason = $"VayuClient HUD artifact at {Path.GetFileName(jarPath)} is not compatible with Minecraft {minecraftVersion} (requires {info.MinecraftCompatibility}).";
                return false;
            }

            return true;
        }

        private static bool IsMinecraftVersionCompatible(string targetMcVersion, string compatibilitySpec, string fileName)
        {
            if (string.IsNullOrWhiteSpace(targetMcVersion)) return true;

            // 1. Strict metadata compatibility spec check
            if (!string.IsNullOrWhiteSpace(compatibilitySpec))
            {
                var tokens = compatibilitySpec.Split(new[] { ' ' }, StringSplitOptions.RemoveEmptyEntries);
                foreach (var token in tokens)
                {
                    if (token.StartsWith(">="))
                    {
                        var minVer = token.Substring(2).Trim();
                        if (CompareVersions(targetMcVersion, minVer) < 0) return false;
                    }
                    else if (token.StartsWith("<="))
                    {
                        var maxVer = token.Substring(2).Trim();
                        if (CompareVersions(targetMcVersion, maxVer) > 0) return false;
                    }
                    else if (token.StartsWith("="))
                    {
                        var exact = token.Substring(1).Trim();
                        if (!string.Equals(targetMcVersion, exact, StringComparison.OrdinalIgnoreCase)) return false;
                    }
                }
            }

            return true;
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

        public void PurgeIncompatibleUiMods(string modsDirectory, int jvmJavaMajor, string minecraftVersion, string? loader = null)
        {
            if (string.IsNullOrEmpty(modsDirectory) || !Directory.Exists(modsDirectory)) return;

            int jvmMaxBytecode = JavaRuntimeService.GetMaxClassFileVersion(jvmJavaMajor);

            try
            {
                // Purge obsolete UI mods and FastClient references unconditionally
                var obsoletePatterns = new[] { "*vayuclient-ui*.jar", "*fastclient*.jar" };
                foreach (var pattern in obsoletePatterns)
                {
                    var stale = Directory.GetFiles(modsDirectory, pattern, SearchOption.TopDirectoryOnly);
                    foreach (var f in stale)
                    {
                        try
                        {
                            CrashLogger.LogMessage($"[VayuHUD] Purging obsolete UI mod: {Path.GetFileName(f)}");
                            File.Delete(f);
                        }
                        catch { }
                    }
                }

                // Check remaining Vayu HUD mods for compatibility
                var files = Directory.GetFiles(modsDirectory, "*vayuclient-hud*.jar", SearchOption.TopDirectoryOnly);
                foreach (var file in files)
                {
                    try
                    {
                        if (!ValidateCompatibility(jvmJavaMajor, file, minecraftVersion, loader ?? "Fabric", out string reason))
                        {
                            CrashLogger.LogMessage($"[VayuHUD] Purging incompatible HUD mod: {Path.GetFileName(file)} ({reason})");
                            File.Delete(file);
                        }
                    }
                    catch (Exception ex)
                    {
                        CrashLogger.LogMessage($"[VayuHUD] Error checking/purging {file}: {ex.Message}");
                    }
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogMessage($"[VayuHUD] Error scanning mods directory: {ex.Message}");
            }
        }
    }
}
