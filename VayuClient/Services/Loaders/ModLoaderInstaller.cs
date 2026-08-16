using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using VayuClient.Models;
using VayuClient.Services.Download;

namespace VayuClient.Services.Loaders
{
    public class ModLoaderInstaller : IModLoaderInstaller
    {
        private readonly IDownloadService _downloadService;
        private readonly string _librariesDir;

        private static readonly HttpClient _http = new()
        {
            Timeout = TimeSpan.FromSeconds(20)
        };

        public ModLoaderInstaller(IDownloadService downloadService)
        {
            _downloadService = downloadService;
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            _librariesDir = Path.Combine(appData, "VayuClient", "libraries");
            Directory.CreateDirectory(_librariesDir);
        }

        public async Task<ModLoaderInstallResult> InstallLoaderAsync(
            MinecraftInstance instance,
            MojangVersionPackage baseMcPkg,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default)
        {
            var result = new ModLoaderInstallResult();

            if (string.IsNullOrEmpty(instance.Loader) || instance.Loader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase))
            {
                result.CustomMainClass = baseMcPkg.MainClass ?? "net.minecraft.client.main.Main";
                return result;
            }

            if (instance.Loader.Equals("Fabric", StringComparison.OrdinalIgnoreCase))
            {
                return await InstallFabricAsync(instance, progress, ct);
            }

            if (instance.Loader.Equals("Quilt", StringComparison.OrdinalIgnoreCase))
            {
                return await InstallQuiltAsync(instance, progress, ct);
            }

            if (instance.Loader.Equals("NeoForge", StringComparison.OrdinalIgnoreCase))
            {
                result.CustomMainClass = "net.neoforged.neoforge.client.ClientEngine";
                return result;
            }

            if (instance.Loader.Equals("Forge", StringComparison.OrdinalIgnoreCase))
            {
                result.CustomMainClass = "cpw.mods.bootstraplauncher.BootstrapLauncher";
                return result;
            }

            return result;
        }

        private async Task<ModLoaderInstallResult> InstallFabricAsync(
            MinecraftInstance instance,
            IProgress<DownloadProgressInfo>? progress,
            CancellationToken ct)
        {
            var result = new ModLoaderInstallResult();

            string loaderVersion = string.IsNullOrEmpty(instance.LoaderVersion) || instance.LoaderVersion.Equals("None", StringComparison.OrdinalIgnoreCase)
                ? "0.16.10"
                : instance.LoaderVersion;

            string url = $"https://meta.fabricmc.net/v2/versions/loader/{Uri.EscapeDataString(instance.MinecraftVersion)}/{Uri.EscapeDataString(loaderVersion)}/profile/json";

            progress?.Report(new DownloadProgressInfo
            {
                CurrentFileName = "Fabric Profile",
                CurrentOperation = $"Fetching Fabric loader profile for {instance.MinecraftVersion}...",
                CompletedFiles = 0,
                TotalFiles = 1
            });

            HttpResponseMessage? response = null;
            try
            {
                using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
                cts.CancelAfter(TimeSpan.FromSeconds(8));
                response = await _http.GetAsync(url, cts.Token);
                if (!response.IsSuccessStatusCode)
                {
                    // Fallback to general loader URL
                    url = $"https://meta.fabricmc.net/v2/versions/loader/{Uri.EscapeDataString(instance.MinecraftVersion)}";
                    response = await _http.GetAsync(url, cts.Token);
                }
            }
            catch (Exception)
            {
                // Network unavailable or timed out: provide resilient offline Fabric defaults
                result.CustomMainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient";
                var defaultLib = Path.Combine(_librariesDir, "net", "fabricmc", "fabric-loader", loaderVersion, $"fabric-loader-{loaderVersion}.jar");
                result.AdditionalLibraries.Add(defaultLib);
                return result;
            }

            if (response == null || !response.IsSuccessStatusCode)
            {
                result.CustomMainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient";
                var defaultLib = Path.Combine(_librariesDir, "net", "fabricmc", "fabric-loader", loaderVersion, $"fabric-loader-{loaderVersion}.jar");
                result.AdditionalLibraries.Add(defaultLib);
                return result;
            }

            var json = await response.Content.ReadAsStringAsync(ct);
            var root = JObject.Parse(json.TrimStart('['));

            result.CustomMainClass = root["mainClass"]?.ToString() ?? "net.fabricmc.loader.impl.launch.knot.KnotClient";

            var libraries = root["libraries"] as JArray;
            if (libraries != null)
            {
                var downloadItems = new List<DownloadItem>();

                foreach (var lib in libraries)
                {
                    var name = lib["name"]?.ToString();
                    var mavenBaseUrl = lib["url"]?.ToString() ?? "https://maven.fabricmc.net/";
                    if (!mavenBaseUrl.EndsWith("/")) mavenBaseUrl += "/";

                    if (!string.IsNullOrEmpty(name))
                    {
                        var relPath = GetMavenPath(name);
                        var destPath = Path.Combine(_librariesDir, relPath);
                        var fileUrl = mavenBaseUrl + relPath.Replace('\\', '/');

                        result.AdditionalLibraries.Add(destPath);

                        downloadItems.Add(new DownloadItem
                        {
                            Url = fileUrl,
                            DestinationPath = destPath,
                            Category = "FabricLibrary",
                            Description = Path.GetFileName(destPath)
                        });
                    }
                }

                if (downloadItems.Count > 0)
                {
                    progress?.Report(new DownloadProgressInfo
                    {
                        CurrentFileName = "Fabric Libraries",
                        CurrentOperation = $"Downloading {downloadItems.Count} Fabric loader libraries...",
                        CompletedFiles = 0,
                        TotalFiles = downloadItems.Count
                    });

                    var batchResult = await _downloadService.DownloadBatchAsync(downloadItems, 8, progress, ct);
                    if (!batchResult.Success)
                    {
                        throw new InvalidOperationException($"Failed to download Fabric libraries: {string.Join(", ", batchResult.Errors.Take(2))}");
                    }
                }
            }

            return result;
        }

        private async Task<ModLoaderInstallResult> InstallQuiltAsync(
            MinecraftInstance instance,
            IProgress<DownloadProgressInfo>? progress,
            CancellationToken ct)
        {
            var result = new ModLoaderInstallResult();

            string loaderVersion = string.IsNullOrEmpty(instance.LoaderVersion) || instance.LoaderVersion.Equals("None", StringComparison.OrdinalIgnoreCase)
                ? "0.26.0"
                : instance.LoaderVersion;

            string url = $"https://meta.quiltmc.org/v3/versions/loader/{Uri.EscapeDataString(instance.MinecraftVersion)}/{Uri.EscapeDataString(loaderVersion)}/profile/json";

            var response = await _http.GetAsync(url, ct);
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync(ct);
                var root = JObject.Parse(json);
                result.CustomMainClass = root["mainClass"]?.ToString() ?? "org.quiltmc.loader.impl.launch.knot.KnotClient";

                var libraries = root["libraries"] as JArray;
                if (libraries != null)
                {
                    var downloadItems = new List<DownloadItem>();
                    foreach (var lib in libraries)
                    {
                        var name = lib["name"]?.ToString();
                        var mavenBaseUrl = lib["url"]?.ToString() ?? "https://maven.quiltmc.org/repository/release/";
                        if (!mavenBaseUrl.EndsWith("/")) mavenBaseUrl += "/";

                        if (!string.IsNullOrEmpty(name))
                        {
                            var relPath = GetMavenPath(name);
                            var destPath = Path.Combine(_librariesDir, relPath);
                            var fileUrl = mavenBaseUrl + relPath.Replace('\\', '/');

                            result.AdditionalLibraries.Add(destPath);
                            downloadItems.Add(new DownloadItem
                            {
                                Url = fileUrl,
                                DestinationPath = destPath,
                                Category = "QuiltLibrary",
                                Description = Path.GetFileName(destPath)
                            });
                        }
                    }

                    if (downloadItems.Count > 0)
                    {
                        await _downloadService.DownloadBatchAsync(downloadItems, 8, progress, ct);
                    }
                }
            }
            else
            {
                result.CustomMainClass = "org.quiltmc.loader.impl.launch.knot.KnotClient";
            }

            return result;
        }

        private static string GetMavenPath(string mavenCoord)
        {
            var parts = mavenCoord.Split(':');
            if (parts.Length < 3) return mavenCoord.Replace(':', '/') + ".jar";

            var group = parts[0].Replace('.', Path.DirectorySeparatorChar);
            var artifact = parts[1];
            var version = parts[2];
            var classifier = parts.Length > 3 ? parts[3] : null;

            if (!string.IsNullOrEmpty(classifier))
            {
                return Path.Combine(group, artifact, version, $"{artifact}-{version}-{classifier}.jar");
            }

            return Path.Combine(group, artifact, version, $"{artifact}-{version}.jar");
        }
    }
}
