using System;
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
                info.ErrorMessage = $"UI JAR file not found at: {jarPath}";
                return info;
            }

            try
            {
                using var zip = ZipFile.OpenRead(jarPath);

                // 1. Read manifest if present
                var manifestEntry = zip.GetEntry("vayuclient-ui-manifest.json");
                if (manifestEntry != null)
                {
                    info.HasManifest = true;
                    try
                    {
                        using var reader = new StreamReader(manifestEntry.Open());
                        var json = reader.ReadToEnd();
                        var obj = JObject.Parse(json);

                        info.VayuUiVersion = obj["vayuUiVersion"]?.ToString() ?? "1.6.1";
                        info.MinecraftCompatibility = obj["minecraftCompatibility"]?.ToString() ?? string.Empty;
                        info.RequiredJavaMajor = obj["requiredJavaMajor"]?.Value<int>() ?? 0;
                        info.BytecodeMajor = obj["bytecodeMajor"]?.Value<int>() ?? 0;

                        var loaders = obj["supportedLoaders"] as JArray;
                        if (loaders != null)
                        {
                            info.SupportedLoaders = loaders.Select(l => l.ToString()).ToList();
                        }
                    }
                    catch (Exception ex)
                    {
                        CrashLogger.LogMessage($"[VayuUI] Warning: could not parse manifest in {jarPath}: {ex.Message}");
                    }
                }

                // 2. Direct binary bytecode inspection on entrypoint class
                var classEntry = zip.GetEntry("com/vayuclient/ui/VayuClientUI.class")
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
            failureReason = string.Empty;
            var info = InspectArtifact(jarPath);

            if (!info.IsValid)
            {
                failureReason = $"VayuClient UI artifact is corrupted or invalid: {info.ErrorMessage}";
                return false;
            }

            int jvmMaxBytecode = JavaRuntimeService.GetMaxClassFileVersion(jvmJavaMajor);

            if (info.BytecodeMajor > jvmMaxBytecode)
            {
                int uiRequiredJava = info.RequiredJavaMajor > 0 ? info.RequiredJavaMajor : (info.BytecodeMajor - 44);
                failureReason = $"VayuClient UI is incompatible with the selected Java runtime.\n" +
                                $"Minecraft {minecraftVersion} is running on Java {jvmJavaMajor} (max bytecode {jvmMaxBytecode}), " +
                                $"but the installed VayuClient UI requires Java {uiRequiredJava} (bytecode {info.BytecodeMajor}).\n" +
                                $"The launcher will install/select a compatible UI build.";
                return false;
            }

            return true;
        }

        public void PurgeIncompatibleUiMods(string modsDirectory, int jvmJavaMajor, string minecraftVersion)
        {
            if (string.IsNullOrEmpty(modsDirectory) || !Directory.Exists(modsDirectory)) return;

            int jvmMaxBytecode = JavaRuntimeService.GetMaxClassFileVersion(jvmJavaMajor);

            try
            {
                var files = Directory.GetFiles(modsDirectory, "vayuclient-ui*.jar", SearchOption.TopDirectoryOnly);
                foreach (var file in files)
                {
                    try
                    {
                        var info = InspectArtifact(file);
                        if (!info.IsValid || info.BytecodeMajor > jvmMaxBytecode)
                        {
                            CrashLogger.LogMessage($"[VayuUI] Purging incompatible/stale UI mod: {Path.GetFileName(file)} (Bytecode {info.BytecodeMajor} > JVM max {jvmMaxBytecode})");
                            File.Delete(file);
                        }
                    }
                    catch (Exception ex)
                    {
                        CrashLogger.LogMessage($"[VayuUI] Error checking/purging {file}: {ex.Message}");
                    }
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogMessage($"[VayuUI] Error scanning mods directory for stale UI mods: {ex.Message}");
            }
        }
    }
}
