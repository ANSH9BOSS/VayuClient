using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Download;
using VayuClient.Services.Java;
using VayuClient.Services.Loaders;
using VayuClient.Services.Minecraft;
using VayuClient.Services.Version;

namespace VayuClient.Services.Integrity
{
    public class InstanceIntegrityService : IInstanceIntegrityService
    {
        private readonly IMinecraftInstaller _minecraftInstaller;
        private readonly IModLoaderInstaller _loaderInstaller;
        private readonly IJavaRuntimeService _javaService;
        private readonly IVersionService _versionService;
        private readonly string _rootDataDir;
        private readonly string _versionsDir;

        private static readonly HttpClient _http = new()
        {
            Timeout = TimeSpan.FromSeconds(15)
        };

        public InstanceIntegrityService(
            IMinecraftInstaller minecraftInstaller,
            IModLoaderInstaller loaderInstaller,
            IJavaRuntimeService javaService,
            IVersionService versionService)
        {
            _minecraftInstaller = minecraftInstaller;
            _loaderInstaller = loaderInstaller;
            _javaService = javaService;
            _versionService = versionService;

            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            _rootDataDir = Path.Combine(appData, "VayuClient");
            _versionsDir = Path.Combine(_rootDataDir, "versions");
        }

        public async Task<InstanceIntegrityReport> ValidateIntegrityAsync(MinecraftInstance instance, CancellationToken ct = default)
        {
            var report = new InstanceIntegrityReport
            {
                RequestedMinecraftVersion = instance.MinecraftVersion,
                RequestedLoader = instance.Loader,
                LoaderVersion = instance.LoaderVersion
            };

            if (string.IsNullOrWhiteSpace(instance.MinecraftVersion))
            {
                report.IsValid = false;
                report.Errors.Add("Instance MinecraftVersion is not defined.");
                return report;
            }

            // 1. Validate version against official Mojang manifest
            try
            {
                var manifestVersions = await _versionService.GetManifestVersionsAsync(forceRefresh: false);
                var match = manifestVersions.FirstOrDefault(v => string.Equals(v.Id, instance.MinecraftVersion, StringComparison.OrdinalIgnoreCase));
                if (match == null)
                {
                    report.Warnings.Add($"Minecraft version '{instance.MinecraftVersion}' was not found in standard Mojang manifest (may be custom snapshot or custom modpack).");
                }
            }
            catch (Exception ex)
            {
                report.Warnings.Add($"Could not query Mojang manifest: {ex.Message}");
            }

            // 2. Validate Version Metadata JSON
            var versionJsonPath = Path.Combine(_versionsDir, instance.MinecraftVersion, $"{instance.MinecraftVersion}.json");
            if (File.Exists(versionJsonPath))
            {
                try
                {
                    var json = await File.ReadAllTextAsync(versionJsonPath, ct);
                    var pkg = JsonConvert.DeserializeObject<MojangVersionPackage>(json);
                    if (pkg != null && !string.IsNullOrEmpty(pkg.Id))
                    {
                        report.DetectedMinecraftVersion = pkg.Id;
                        if (!string.Equals(pkg.Id, instance.MinecraftVersion, StringComparison.OrdinalIgnoreCase))
                        {
                            report.IsValid = false;
                            report.Errors.Add($"Version mismatch in metadata JSON: expected '{instance.MinecraftVersion}', found '{pkg.Id}'.");
                        }
                        else
                        {
                            report.VersionJsonValid = true;
                        }
                    }
                    else
                    {
                        report.IsValid = false;
                        report.Errors.Add($"Version JSON '{versionJsonPath}' is invalid or empty.");
                    }
                }
                catch (Exception ex)
                {
                    report.IsValid = false;
                    report.Errors.Add($"Failed to parse version JSON: {ex.Message}");
                }
            }
            else
            {
                // Not yet downloaded
                report.VersionJsonValid = false;
            }

            // 3. Validate Client JAR
            var clientJarPath = Path.Combine(_versionsDir, instance.MinecraftVersion, $"{instance.MinecraftVersion}.jar");
            if (File.Exists(clientJarPath))
            {
                var fileInfo = new FileInfo(clientJarPath);
                if (fileInfo.Length > 1024)
                {
                    report.ClientJarExists = true;
                }
                else
                {
                    report.IsValid = false;
                    report.Errors.Add($"Client JAR '{clientJarPath}' exists but is corrupt or 0 bytes.");
                }
            }

            // 4. Validate Loader Compatibility
            if (!string.IsNullOrEmpty(instance.Loader) && !instance.Loader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase))
            {
                report.DetectedLoader = instance.Loader;
                if (instance.Loader.Equals("Fabric", StringComparison.OrdinalIgnoreCase))
                {
                    try
                    {
                        var fabricUrl = $"https://meta.fabricmc.net/v2/versions/loader/{Uri.EscapeDataString(instance.MinecraftVersion)}";
                        var response = await _http.GetAsync(fabricUrl, ct);
                        if (response.IsSuccessStatusCode)
                        {
                            var content = await response.Content.ReadAsStringAsync(ct);
                            var token = JToken.Parse(content);
                            if (token is JArray arr && arr.Count > 0)
                            {
                                report.LoaderCompatible = true;
                            }
                            else
                            {
                                report.IsValid = false;
                                report.LoaderCompatible = false;
                                report.Errors.Add($"No compatible Fabric Loader was found for Minecraft {instance.MinecraftVersion}.");
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        report.Warnings.Add($"Could not verify online Fabric compatibility: {ex.Message}");
                    }
                }
            }

            // 5. Validate Java Runtime Compatibility
            try
            {
                int reqJava = _javaService.GetRequiredJavaVersion(instance.MinecraftVersion);
                var runtime = _javaService.FindCompatibleRuntime(reqJava);
                if (runtime != null && runtime.MajorVersion < reqJava)
                {
                    report.IsValid = false;
                    report.JavaCompatible = false;
                    report.Errors.Add($"Incompatible Java: Minecraft {instance.MinecraftVersion} requires Java {reqJava}+, but found Java {runtime.MajorVersion}.");
                }
            }
            catch (Exception ex)
            {
                report.Warnings.Add($"Java compatibility check failed: {ex.Message}");
            }

            return report;
        }

        public async Task<bool> RepairInstanceAsync(
            MinecraftInstance instance,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default)
        {
            CrashLogger.LogMessage($"[InstanceIntegrity]: Starting automated repair for instance '{instance.Name}' (MC {instance.MinecraftVersion}, Loader: {instance.Loader})...");

            try
            {
                // 1. Ensure game directory exists and isolate user data
                Directory.CreateDirectory(instance.GameDirectory);
                var nativesDir = Path.Combine(Path.GetDirectoryName(instance.GameDirectory) ?? _rootDataDir, "natives");
                Directory.CreateDirectory(nativesDir);

                // 2. Re-download and verify official Mojang Version Package
                progress?.Report(new DownloadProgressInfo
                {
                    CurrentOperation = $"Resolving official Minecraft {instance.MinecraftVersion} metadata...",
                    CompletedFiles = 0,
                    TotalFiles = 1
                });

                var pkg = await _minecraftInstaller.GetVersionPackageAsync(instance.MinecraftVersion, ct);
                if (pkg == null || !string.Equals(pkg.Id, instance.MinecraftVersion, StringComparison.OrdinalIgnoreCase))
                {
                    throw new InvalidOperationException($"Failed to obtain valid Mojang package for Minecraft {instance.MinecraftVersion}.");
                }

                // 3. Re-install Minecraft Client & Assets & Natives
                bool mcOk = await _minecraftInstaller.InstallMinecraftAsync(instance.MinecraftVersion, nativesDir, progress, ct);
                if (!mcOk)
                {
                    throw new InvalidOperationException($"Minecraft installation failed during repair of '{instance.Name}'.");
                }

                // 4. Re-install Mod Loader if applicable
                if (!string.IsNullOrEmpty(instance.Loader) && !instance.Loader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase))
                {
                    var loaderResult = await _loaderInstaller.InstallLoaderAsync(instance, pkg, progress, ct);
                    if (!loaderResult.Success)
                    {
                        throw new InvalidOperationException($"Loader installation failed during repair: {loaderResult.ErrorMessage}");
                    }
                }

                // 5. Ensure Java Runtime is provisioned
                int reqJava = _javaService.GetRequiredJavaVersion(instance.MinecraftVersion, pkg.JavaVersion?.MajorVersion ?? 0);
                var java = _javaService.FindCompatibleRuntime(reqJava);
                if (java == null)
                {
                    await _javaService.EnsureJavaRuntimeAsync(reqJava, progress, ct);
                }

                CrashLogger.LogMessage($"[InstanceIntegrity]: Instance '{instance.Name}' repaired successfully.");
                return true;
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("InstanceRepair", ex);
                return false;
            }
        }
    }
}
