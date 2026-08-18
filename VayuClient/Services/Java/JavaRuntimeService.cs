using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Win32;
using Newtonsoft.Json.Linq;
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

                // 4. Mojang & VayuClient Launcher runtimes
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);

                var managedRuntimeDirs = new[]
                {
                    Path.Combine(appData, "VayuClient", "runtimes"),
                    Path.Combine(localAppData, "VayuClient", "runtimes"),
                    Path.Combine(appData, ".minecraft", "runtime"),
                    Path.Combine(localAppData, "Packages", "Microsoft.4297127D64C57_8wekyb3d8bbwe", "LocalCache", "Local", "runtime")
                };

                foreach (var mDir in managedRuntimeDirs)
                {
                    if (Directory.Exists(mDir))
                    {
                        try
                        {
                            foreach (var compDir in Directory.GetDirectories(mDir))
                            {
                                AddJavaExeIfValid(candidatePaths, Path.Combine(compDir, "bin", "javaw.exe"));
                                AddJavaExeIfValid(candidatePaths, Path.Combine(compDir, "bin", "java.exe"));

                                foreach (var archDir in Directory.GetDirectories(compDir))
                                {
                                    AddJavaExeIfValid(candidatePaths, Path.Combine(archDir, "bin", "javaw.exe"));
                                    AddJavaExeIfValid(candidatePaths, Path.Combine(archDir, "bin", "java.exe"));

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
            // Minecraft 26.x strictly requires Java 25 or later (never downgrade to 21)
            if (MinecraftVersionComparer.CompareVersionStrings(minecraftVersion, "26") >= 0 ||
                minecraftVersion.StartsWith("26.", StringComparison.OrdinalIgnoreCase) ||
                minecraftVersion.Contains("26w", StringComparison.OrdinalIgnoreCase) ||
                minecraftVersion.StartsWith("26-", StringComparison.OrdinalIgnoreCase))
            {
                return Math.Max(25, manifestMajorVersion);
            }

            if (manifestMajorVersion > 0)
            {
                return manifestMajorVersion;
            }

            // Modern 1.20.5+
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

            // 1. Exact match for required version (e.g. 21, 17, 8, 16) - 64-bit first
            var exact64 = runtimes.FirstOrDefault(r => r.MajorVersion == requiredMajorVersion && r.Is64Bit);
            if (exact64 != null) return exact64;

            var exact = runtimes.FirstOrDefault(r => r.MajorVersion == requiredMajorVersion);
            if (exact != null) return exact;

            // 2. Proximity match (choose the closest compatible version >= requiredMajorVersion, 64-bit preferred)
            var compatible = runtimes
                .Where(r => r.MajorVersion >= requiredMajorVersion)
                .OrderBy(r => !r.Is64Bit) // 64-bit first
                .ThenBy(r => Math.Abs(r.MajorVersion - requiredMajorVersion)) // Closest to requested version first (e.g. 21 before 25)
                .FirstOrDefault();

            if (compatible != null) return compatible;

            // 3. Fallback for legacy Java 8: accept Java 11 or 17
            if (requiredMajorVersion <= 8)
            {
                return runtimes.OrderBy(r => !r.Is64Bit).ThenBy(r => r.MajorVersion).FirstOrDefault();
            }

            return null;
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

        public async Task<JavaRuntimeInfo?> EnsureJavaRuntimeAsync(
            int requiredMajorVersion,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default)
        {
            // 1. Check if already compatible runtime installed on PC
            var existing = FindCompatibleRuntime(requiredMajorVersion);
            if (existing != null)
            {
                return existing;
            }

            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var runtimesRoot = Path.Combine(appData, "VayuClient", "runtimes");
            Directory.CreateDirectory(runtimesRoot);

            var targetDir = Path.Combine(runtimesRoot, $"java-{requiredMajorVersion}");

            // 2. Check if target directory already has a valid runtime on disk
            if (Directory.Exists(targetDir))
            {
                var existingExe = FindJavaExeInDirectory(targetDir);
                if (!string.IsNullOrEmpty(existingExe))
                {
                    var probed = ProbeJavaRuntime(existingExe);
                    if (probed != null && probed.MajorVersion >= requiredMajorVersion)
                    {
                        lock (_lock)
                        {
                            _cachedRuntimes?.Insert(0, probed);
                        }
                        return probed;
                    }
                }
            }

            Directory.CreateDirectory(targetDir);

            // 3. Download standalone OpenJDK JRE / JDK archive
            using var httpClient = new HttpClient { Timeout = TimeSpan.FromMinutes(10) };
            httpClient.DefaultRequestHeaders.UserAgent.ParseAdd($"VayuClient/{Core.AppInfo.VersionString} (Windows x64)");

            string? tempZip = null;
            try
            {
                // Try Adoptium Temurin official OpenJDK builds first
                string downloadUrl;
                if (requiredMajorVersion == 21)
                {
                    downloadUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse";
                }
                else if (requiredMajorVersion == 17)
                {
                    downloadUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse";
                }
                else if (requiredMajorVersion <= 8)
                {
                    downloadUrl = "https://api.adoptium.net/v3/binary/latest/8/ga/windows/x64/jre/hotspot/normal/eclipse";
                }
                else if (requiredMajorVersion >= 25)
                {
                    // Check Mojang's official all.json runtime manifest for java-runtime-epsilon
                    downloadUrl = await ResolveMojangRuntimeManifestUrlAsync("java-runtime-epsilon", httpClient, ct)
                        ?? "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse";
                }
                else
                {
                    downloadUrl = $"https://api.adoptium.net/v3/binary/latest/{requiredMajorVersion}/ga/windows/x64/jre/hotspot/normal/eclipse";
                }

                // If downloadUrl is a Mojang package manifest JSON
                if (downloadUrl.EndsWith(".json", StringComparison.OrdinalIgnoreCase) || downloadUrl.Contains("piston-meta"))
                {
                    await DownloadMojangRuntimeFilesAsync(downloadUrl, targetDir, httpClient, progress, ct);
                }
                else
                {
                    // Standard Zip download & extraction
                    tempZip = Path.Combine(Path.GetTempPath(), $"vayu_java_{requiredMajorVersion}_{Guid.NewGuid():N}.zip");
                    await DownloadFileWithProgressAsync(httpClient, downloadUrl, tempZip, $"OpenJDK {requiredMajorVersion}", progress, ct);

                    // Extract zip to targetDir
                    ZipFile.ExtractToDirectory(tempZip, targetDir, overwriteFiles: true);
                }

                // 4. Locate javaw.exe or java.exe in targetDir
                var installedExe = FindJavaExeInDirectory(targetDir);
                if (string.IsNullOrEmpty(installedExe))
                {
                    throw new FileNotFoundException($"Could not locate javaw.exe inside {targetDir} after extraction.");
                }

                var runtimeInfo = ProbeJavaRuntime(installedExe);
                if (runtimeInfo == null)
                {
                    runtimeInfo = new JavaRuntimeInfo
                    {
                        Path = installedExe,
                        Version = $"{requiredMajorVersion}.0.0",
                        MajorVersion = requiredMajorVersion,
                        Vendor = "VayuClient Managed OpenJDK",
                        Is64Bit = true,
                        Architecture = "x64"
                    };
                }

                lock (_lock)
                {
                    _cachedRuntimes?.Insert(0, runtimeInfo);
                }

                return runtimeInfo;
            }
            finally
            {
                if (!string.IsNullOrEmpty(tempZip) && File.Exists(tempZip))
                {
                    try { File.Delete(tempZip); } catch { }
                }
            }
        }

        private static string? FindJavaExeInDirectory(string rootDir)
        {
            if (!Directory.Exists(rootDir)) return null;

            // Direct bin/javaw.exe
            var direct = Path.Combine(rootDir, "bin", "javaw.exe");
            if (File.Exists(direct)) return direct;
            var directJava = Path.Combine(rootDir, "bin", "java.exe");
            if (File.Exists(directJava)) return directJava;

            // Recursive search
            try
            {
                var files = Directory.GetFiles(rootDir, "javaw.exe", SearchOption.AllDirectories);
                if (files.Length > 0) return files[0];

                var javaFiles = Directory.GetFiles(rootDir, "java.exe", SearchOption.AllDirectories);
                if (javaFiles.Length > 0) return javaFiles[0];
            }
            catch { }

            return null;
        }

        private static async Task DownloadFileWithProgressAsync(
            HttpClient httpClient,
            string url,
            string destinationPath,
            string itemName,
            IProgress<DownloadProgressInfo>? progress,
            CancellationToken ct)
        {
            using var response = await httpClient.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct);
            response.EnsureSuccessStatusCode();

            var totalBytes = response.Content.Headers.ContentLength ?? 0L;
            await using var contentStream = await response.Content.ReadAsStreamAsync(ct);
            await using var fileStream = new FileStream(destinationPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);

            var buffer = new byte[81920];
            long totalRead = 0;
            int read;

            while ((read = await contentStream.ReadAsync(buffer.AsMemory(0, buffer.Length), ct)) > 0)
            {
                await fileStream.WriteAsync(buffer.AsMemory(0, read), ct);
                totalRead += read;

                if (progress != null)
                {
                    progress.Report(new DownloadProgressInfo
                    {
                        TotalBytes = totalBytes,
                        BytesReceived = totalRead,
                        CurrentFileName = itemName,
                        CurrentOperation = $"Downloading {itemName} ({(totalRead / (1024.0 * 1024.0)):F1} MB / {(totalBytes > 0 ? (totalBytes / (1024.0 * 1024.0)).ToString("F1") : "...")} MB)"
                    });
                }
            }
        }

        private static async Task<string?> ResolveMojangRuntimeManifestUrlAsync(string runtimeName, HttpClient client, CancellationToken ct)
        {
            try
            {
                var allJson = await client.GetStringAsync("https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json", ct);
                var root = JObject.Parse(allJson);
                var win64 = root["windows-x64"]?[runtimeName] as JArray;
                if (win64 != null && win64.Count > 0)
                {
                    return win64[0]?["manifest"]?["url"]?.ToString();
                }
            }
            catch { }
            return null;
        }

        private static async Task DownloadMojangRuntimeFilesAsync(
            string manifestUrl,
            string targetDir,
            HttpClient client,
            IProgress<DownloadProgressInfo>? progress,
            CancellationToken ct)
        {
            var manifestJson = await client.GetStringAsync(manifestUrl, ct);
            var manifest = JObject.Parse(manifestJson);
            var files = manifest["files"] as JObject;
            if (files == null) return;

            int totalCount = files.Count;
            int currentCount = 0;

            // First pass: create all required directories
            foreach (var prop in files.Properties())
            {
                var relPath = prop.Name.Replace('/', Path.DirectorySeparatorChar);
                var fullPath = Path.Combine(targetDir, relPath);
                var fileObj = prop.Value as JObject;
                var type = fileObj?["type"]?.ToString();

                if (type == "directory")
                {
                    Directory.CreateDirectory(fullPath);
                }
                else
                {
                    var parent = Path.GetDirectoryName(fullPath);
                    if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                }
            }

            // Second pass: parallel download of files
            using var semaphore = new SemaphoreSlim(16, 16);
            var fileProperties = files.Properties().Where(p => (p.Value as JObject)?["type"]?.ToString() == "file").ToList();

            var downloadTasks = fileProperties.Select(async prop =>
            {
                await semaphore.WaitAsync(ct);
                try
                {
                    ct.ThrowIfCancellationRequested();
                    var relPath = prop.Name.Replace('/', Path.DirectorySeparatorChar);
                    var fullPath = Path.Combine(targetDir, relPath);
                    var fileObj = prop.Value as JObject;
                    var rawUrl = fileObj?["downloads"]?["raw"]?["url"]?.ToString();

                    if (!string.IsNullOrEmpty(rawUrl))
                    {
                        if (!File.Exists(fullPath) || new FileInfo(fullPath).Length == 0)
                        {
                            var bytes = await client.GetByteArrayAsync(rawUrl, ct);
                            await File.WriteAllBytesAsync(fullPath, bytes, ct);
                        }
                    }

                    int finished = Interlocked.Increment(ref currentCount);
                    progress?.Report(new DownloadProgressInfo
                    {
                        TotalFiles = totalCount,
                        CompletedFiles = finished,
                        CurrentFileName = Path.GetFileName(fullPath),
                        CurrentOperation = $"Installing Java files ({finished}/{totalCount})..."
                    });
                }
                finally
                {
                    semaphore.Release();
                }
            });

            await Task.WhenAll(downloadTasks);
        }

        /// <summary>
        /// Maps a Java runtime major version to the maximum supported Java class file bytecode version.
        /// JVM supports class file versions up to 44 + Major (e.g. Java 8 = 52, Java 17 = 61, Java 21 = 65, Java 25 = 69).
        /// </summary>
        public static int GetMaxClassFileVersion(int javaMajor)
        {
            if (javaMajor <= 0) return 65; // Default assumption
            if (javaMajor == 1) return 45;
            return 44 + javaMajor;
        }

        /// <summary>
        /// Determines the standard required Java major version for a given Minecraft version string.
        /// </summary>
        public static int GetRequiredJavaMajorForMinecraft(string minecraftVersion)
        {
            if (string.IsNullOrWhiteSpace(minecraftVersion)) return 21;
            var clean = minecraftVersion.Trim();

            // Modern versions (1.20.5+, 1.21.x, 26.x) require Java 21
            if (clean.StartsWith("26.") || clean.StartsWith("1.21") || clean.StartsWith("1.20.5") || clean.StartsWith("1.20.6"))
            {
                return 21;
            }

            // 1.18 to 1.20.4 require Java 17
            if (clean.StartsWith("1.18") || clean.StartsWith("1.19") || clean.StartsWith("1.20"))
            {
                return 17;
            }

            // 1.17 requires Java 16
            if (clean.StartsWith("1.17"))
            {
                return 16;
            }

            // 1.16.5 and older run on Java 8
            return 8;
        }
    }
}
