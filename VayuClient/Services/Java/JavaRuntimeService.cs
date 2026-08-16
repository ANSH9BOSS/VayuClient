using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Microsoft.Win32;
using VayuClient.Models;
using VayuClient.Services.Version;

namespace VayuClient.Services.Java
{
    public class JavaRuntimeService : IJavaRuntimeService
    {
        private List<JavaRuntimeInfo>? _cachedRuntimes;
        private readonly object _lock = new();

        public List<JavaRuntimeInfo> DetectInstalledRuntimes()
        {
            lock (_lock)
            {
                if (_cachedRuntimes != null && _cachedRuntimes.Count > 0)
                {
                    return _cachedRuntimes;
                }

                var candidatePaths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

                // 1. Environment variables
                var envVars = new[] { "JAVA_HOME", "JDK_HOME", "JRE_HOME", "GRAALVM_HOME" };
                foreach (var ev in envVars)
                {
                    var val = Environment.GetEnvironmentVariable(ev);
                    if (!string.IsNullOrEmpty(val))
                    {
                        AddJavaExeIfValid(candidatePaths, Path.Combine(val, "bin", "javaw.exe"));
                        AddJavaExeIfValid(candidatePaths, Path.Combine(val, "bin", "java.exe"));
                    }
                }

                // 2. PATH
                var pathVar = Environment.GetEnvironmentVariable("PATH");
                if (!string.IsNullOrEmpty(pathVar))
                {
                    foreach (var p in pathVar.Split(Path.PathSeparator, StringSplitOptions.RemoveEmptyEntries))
                    {
                        AddJavaExeIfValid(candidatePaths, Path.Combine(p, "javaw.exe"));
                        AddJavaExeIfValid(candidatePaths, Path.Combine(p, "java.exe"));
                    }
                }

                // 3. Standard Program Files Directories
                var programDirs = new[]
                {
                    Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
                    Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86),
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs")
                };

                var javaRootFolders = new[]
                {
                    "Java", "Eclipse Adoptium", "Eclipse Foundation", "Microsoft", "Amazon Corretto", "Zulu", "BellSoft", "Semeru", "AdoptOpenJDK"
                };

                foreach (var pDir in programDirs)
                {
                    if (string.IsNullOrEmpty(pDir) || !Directory.Exists(pDir)) continue;

                    foreach (var jRoot in javaRootFolders)
                    {
                        var targetRoot = Path.Combine(pDir, jRoot);
                        if (!Directory.Exists(targetRoot)) continue;

                        try
                        {
                            foreach (var subDir in Directory.GetDirectories(targetRoot))
                            {
                                AddJavaExeIfValid(candidatePaths, Path.Combine(subDir, "bin", "javaw.exe"));
                                AddJavaExeIfValid(candidatePaths, Path.Combine(subDir, "bin", "java.exe"));
                            }
                        }
                        catch { }
                    }
                }

                // 4. Mojang Launcher runtimes
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);

                var mojangRuntimeDirs = new[]
                {
                    Path.Combine(appData, ".minecraft", "runtime"),
                    Path.Combine(localAppData, "Packages", "Microsoft.4297127D64C57_8wekyb3d8bbwe", "LocalCache", "Local", "runtime")
                };

                foreach (var mDir in mojangRuntimeDirs)
                {
                    if (Directory.Exists(mDir))
                    {
                        try
                        {
                            foreach (var compDir in Directory.GetDirectories(mDir))
                            {
                                foreach (var archDir in Directory.GetDirectories(compDir))
                                {
                                    foreach (var sub in Directory.GetDirectories(archDir))
                                    {
                                        AddJavaExeIfValid(candidatePaths, Path.Combine(sub, "bin", "javaw.exe"));
                                        AddJavaExeIfValid(candidatePaths, Path.Combine(sub, "bin", "java.exe"));
                                    }
                                }
                            }
                        }
                        catch { }
                    }
                }

                // 5. Query candidate runtimes for details
                var runtimes = new List<JavaRuntimeInfo>();
                foreach (var exePath in candidatePaths)
                {
                    var info = ProbeJavaRuntime(exePath);
                    if (info != null && info.MajorVersion > 0)
                    {
                        // Avoid duplicates of same path
                        if (!runtimes.Any(r => string.Equals(r.Path, info.Path, StringComparison.OrdinalIgnoreCase)))
                        {
                            runtimes.Add(info);
                        }
                    }
                }

                // Sort by prioritized standard LTS versions (21, 17, 8) over experimental/preview versions (25+)
                runtimes = runtimes
                    .OrderBy(r => GetLtsPriority(r.MajorVersion))
                    .ThenBy(r => r.Path.Contains(".minecraft", StringComparison.OrdinalIgnoreCase) ? 1 : 0)
                    .ThenByDescending(r => r.Is64Bit)
                    .ToList();
                _cachedRuntimes = runtimes;
                return runtimes;
            }
        }

        private static int GetLtsPriority(int major)
        {
            // Lower number = higher priority
            return major switch
            {
                21 => 1,
                17 => 2,
                8 => 3,
                22 => 4,
                11 => 5,
                16 => 6,
                _ => 10 + major // Unofficial or preview versions like 25+ de-prioritized
            };
        }

        public int GetRequiredJavaVersion(string minecraftVersion, int manifestMajorVersion = 0)
        {
            if (manifestMajorVersion > 0)
            {
                // Mod loaders (Fabric, Forge, NeoForge, Quilt) use ASM which currently only supports up to Java 21/22 bytecode (class version <= 66).
                // Java 25 preview produces class version 69 which crashes Fabric ClassReader.
                if (manifestMajorVersion > 21)
                {
                    return 21;
                }
                return manifestMajorVersion;
            }

            // Modern snapshot / 26.x or 1.20.5+
            if (MinecraftVersionComparer.CompareVersionStrings(minecraftVersion, "1.20.5") >= 0)
            {
                return 21;
            }

            // 1.18 to 1.20.4
            if (MinecraftVersionComparer.CompareVersionStrings(minecraftVersion, "1.18") >= 0)
            {
                return 17;
            }

            // 1.17
            if (MinecraftVersionComparer.CompareVersionStrings(minecraftVersion, "1.17") >= 0)
            {
                return 16;
            }

            // Legacy < 1.17
            return 8;
        }

        public JavaRuntimeInfo? FindCompatibleRuntime(int requiredMajorVersion)
        {
            var runtimes = DetectInstalledRuntimes();
            if (runtimes.Count == 0) return null;

            // Strict cap to Java 21 LTS: Prevents picking Java 25 (bytecode 69) which breaks Fabric/Forge ASM reader
            int targetMajor = requiredMajorVersion > 21 ? 21 : requiredMajorVersion;

            // 1. Exact match for target LTS version (e.g. 21 == 21, 17 == 17, 8 == 8)
            var exact = runtimes.FirstOrDefault(r => r.MajorVersion == targetMajor && r.Is64Bit);
            if (exact != null) return exact;

            // 2. For Java >= 21 (Minecraft 1.20.5+ / 26.x): Strictly Java 21 LTS or 22
            if (targetMajor >= 21)
            {
                var stableModern = runtimes.FirstOrDefault(r => r.MajorVersion == 21 && r.Is64Bit)
                                   ?? runtimes.FirstOrDefault(r => r.MajorVersion == 22 && r.Is64Bit);
                if (stableModern != null) return stableModern;
            }

            // 3. For Java 17 (Minecraft 1.18 - 1.20.4): Java 17 or 21
            if (targetMajor == 17)
            {
                var java17 = runtimes.FirstOrDefault(r => r.MajorVersion == 17 && r.Is64Bit)
                             ?? runtimes.FirstOrDefault(r => r.MajorVersion == 21 && r.Is64Bit);
                if (java17 != null) return java17;
            }

            // 4. For Java 16: Java 16, 17 or 21
            if (targetMajor == 16)
            {
                var java16 = runtimes.FirstOrDefault(r => r.MajorVersion == 16 && r.Is64Bit)
                             ?? runtimes.FirstOrDefault(r => r.MajorVersion == 17 && r.Is64Bit)
                             ?? runtimes.FirstOrDefault(r => r.MajorVersion == 21 && r.Is64Bit);
                if (java16 != null) return java16;
            }

            // 5. For legacy Java 8: Java 8 or 11
            if (targetMajor <= 8)
            {
                var java8 = runtimes.FirstOrDefault(r => r.MajorVersion == 8 && r.Is64Bit)
                            ?? runtimes.FirstOrDefault(r => r.MajorVersion == 11 && r.Is64Bit);
                if (java8 != null) return java8;
            }

            // 6. Safe fallback (strictly avoid preview versions > 22 unless no other 64-bit runtime exists)
            var safeFallback = runtimes.FirstOrDefault(r => r.MajorVersion >= targetMajor && r.MajorVersion <= 22 && r.Is64Bit)
                               ?? runtimes.FirstOrDefault(r => r.Is64Bit && r.MajorVersion <= 22)
                               ?? runtimes.FirstOrDefault(r => r.Is64Bit)
                               ?? runtimes.FirstOrDefault();
            return safeFallback;
        }

        private static void AddJavaExeIfValid(HashSet<string> paths, string path)
        {
            if (File.Exists(path))
            {
                paths.Add(Path.GetFullPath(path));
            }
        }

        private static JavaRuntimeInfo? ProbeJavaRuntime(string exePath)
        {
            try
            {
                var startInfo = new ProcessStartInfo
                {
                    FileName = exePath,
                    Arguments = "-version",
                    RedirectStandardError = true,
                    RedirectStandardOutput = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };

                using var process = Process.Start(startInfo);
                if (process == null) return null;

                string output = process.StandardError.ReadToEnd() + Environment.NewLine + process.StandardOutput.ReadToEnd();
                if (!process.WaitForExit(2000))
                {
                    try { process.Kill(); } catch { }
                    return null;
                }

                // Parse version string: e.g. "21.0.3" or "1.8.0_411"
                var versionMatch = Regex.Match(output, @"version\s+""([^""]+)""");
                string versionStr = versionMatch.Success ? versionMatch.Groups[1].Value : "Unknown";

                int majorVersion = 0;
                if (versionStr.StartsWith("1."))
                {
                    var subMatch = Regex.Match(versionStr, @"1\.(\d+)");
                    if (subMatch.Success) int.TryParse(subMatch.Groups[1].Value, out majorVersion);
                }
                else
                {
                    var majorMatch = Regex.Match(versionStr, @"^(\d+)");
                    if (majorMatch.Success) int.TryParse(majorMatch.Groups[1].Value, out majorVersion);
                }

                // Detect vendor
                string vendor = "Java";
                if (output.Contains("Temurin", StringComparison.OrdinalIgnoreCase) || output.Contains("Adoptium", StringComparison.OrdinalIgnoreCase))
                    vendor = "Eclipse Adoptium";
                else if (output.Contains("Microsoft", StringComparison.OrdinalIgnoreCase))
                    vendor = "Microsoft";
                else if (output.Contains("Corretto", StringComparison.OrdinalIgnoreCase) || output.Contains("Amazon", StringComparison.OrdinalIgnoreCase))
                    vendor = "Amazon Corretto";
                else if (output.Contains("Zulu", StringComparison.OrdinalIgnoreCase) || output.Contains("Azul", StringComparison.OrdinalIgnoreCase))
                    vendor = "Azul Zulu";
                else if (output.Contains("OpenJDK", StringComparison.OrdinalIgnoreCase))
                    vendor = "OpenJDK";
                else if (output.Contains("Oracle", StringComparison.OrdinalIgnoreCase) || output.Contains("Java(TM)", StringComparison.OrdinalIgnoreCase))
                    vendor = "Oracle";

                bool is64Bit = output.Contains("64-Bit", StringComparison.OrdinalIgnoreCase) || output.Contains("x86_64", StringComparison.OrdinalIgnoreCase) || output.Contains("amd64", StringComparison.OrdinalIgnoreCase);

                return new JavaRuntimeInfo
                {
                    Path = exePath,
                    Version = versionStr,
                    MajorVersion = majorVersion,
                    Vendor = vendor,
                    Is64Bit = is64Bit,
                    Architecture = is64Bit ? "x64" : "x86"
                };
            }
            catch
            {
                return null;
            }
        }
    }
}
