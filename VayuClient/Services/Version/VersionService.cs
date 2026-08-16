using System.IO;
using System.Net.Http;
using System.Text.Json;
using Newtonsoft.Json.Linq;
using VayuClient.Models;

namespace VayuClient.Services.Version
{
    public class VersionService : IVersionService
    {
        private static readonly HttpClient _http = new()
        {
            Timeout = TimeSpan.FromSeconds(30)
        };

        private readonly string _cacheFilePath;
        private List<MinecraftVersion>? _cachedManifestVersions;

        public string LatestRelease { get; private set; } = "1.21.5";
        public string LatestSnapshot { get; private set; } = "1.21.5-pre1";

        public VersionService()
        {
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var cacheDir = Path.Combine(appData, "VayuClient", "cache");
            Directory.CreateDirectory(cacheDir);
            _cacheFilePath = Path.Combine(cacheDir, "version_manifest.json");

            if (!_http.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _http.DefaultRequestHeaders.Add("User-Agent", Core.AppInfo.UserAgent);
            }

            // Instantly initialize with local cache if available, or rich pre-warmed fallback list
            if (File.Exists(_cacheFilePath))
            {
                try
                {
                    var json = File.ReadAllText(_cacheFilePath);
                    var cached = ParseManifest(json);
                    if (cached.Count > 0)
                    {
                        _cachedManifestVersions = cached;
                    }
                }
                catch { }
            }

            if (_cachedManifestVersions == null || _cachedManifestVersions.Count == 0)
            {
                _cachedManifestVersions = GetHardcodedFallbackList();
            }

            // Refresh manifest in background without blocking UI
            Task.Run(async () =>
            {
                try
                {
                    await FetchAndCacheManifestAsync();
                }
                catch { }
            });
        }

        public async Task<List<MinecraftVersion>> GetManifestVersionsAsync(bool forceRefresh = false)
        {
            if (!forceRefresh && _cachedManifestVersions != null && _cachedManifestVersions.Count > 0)
            {
                return _cachedManifestVersions;
            }

            if (forceRefresh)
            {
                await FetchAndCacheManifestAsync();
            }

            return _cachedManifestVersions ?? GetHardcodedFallbackList();
        }

        private async Task FetchAndCacheManifestAsync()
        {
            try
            {
                using var cts = new System.Threading.CancellationTokenSource(TimeSpan.FromSeconds(8));
                var response = await _http.GetAsync("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json", cts.Token);
                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    var list = ParseManifest(json);
                    if (list.Count > 0)
                    {
                        _cachedManifestVersions = list;
                        await File.WriteAllTextAsync(_cacheFilePath, json);
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Failed to fetch Mojang manifest: {ex.Message}");
            }
        }

        private List<MinecraftVersion> ParseManifest(string json)
        {
            var versions = new List<MinecraftVersion>();

            try
            {
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;

                if (root.TryGetProperty("latest", out var latest))
                {
                    if (latest.TryGetProperty("release", out var rel))
                        LatestRelease = rel.GetString() ?? "1.21.11";
                    if (latest.TryGetProperty("snapshot", out var snap))
                        LatestSnapshot = snap.GetString() ?? "26.2";
                }

                if (root.TryGetProperty("versions", out var verArray))
                {
                    foreach (var elem in verArray.EnumerateArray())
                    {
                        var id = elem.GetProperty("id").GetString() ?? "";
                        var type = elem.GetProperty("type").GetString() ?? "release";
                        var url = elem.GetProperty("url").GetString() ?? "";
                        var timeStr = elem.GetProperty("time").GetString() ?? "";
                        var relTimeStr = elem.GetProperty("releaseTime").GetString() ?? timeStr;

                        DateTime.TryParse(relTimeStr, out var releaseDate);

                        versions.Add(new MinecraftVersion
                        {
                            Id = id,
                            Type = type,
                            Url = url,
                            ReleaseDate = releaseDate
                        });
                    }
                }
            }
            catch { }

            return versions;
        }

        private static List<MinecraftVersion> GetHardcodedFallbackList()
        {
            return new List<MinecraftVersion>
            {
                new() { Id = "26.2", Type = "release", ReleaseDate = new DateTime(2026, 4, 1) },
                new() { Id = "1.21.11", Type = "release", ReleaseDate = new DateTime(2025, 12, 9) },
                new() { Id = "1.21.10", Type = "release", ReleaseDate = new DateTime(2025, 10, 7) },
                new() { Id = "1.21.9", Type = "release", ReleaseDate = new DateTime(2025, 9, 30) },
                new() { Id = "1.21.8", Type = "release", ReleaseDate = new DateTime(2025, 7, 17) },
                new() { Id = "1.21.7", Type = "release", ReleaseDate = new DateTime(2025, 6, 30) },
                new() { Id = "1.21.6", Type = "release", ReleaseDate = new DateTime(2025, 6, 17) },
                new() { Id = "1.21.5", Type = "release", ReleaseDate = new DateTime(2025, 3, 25) },
                new() { Id = "1.21.4", Type = "release", ReleaseDate = new DateTime(2024, 12, 3) },
                new() { Id = "1.21.3", Type = "release", ReleaseDate = new DateTime(2024, 10, 23) },
                new() { Id = "1.21.1", Type = "release", ReleaseDate = new DateTime(2024, 8, 8) },
                new() { Id = "1.21", Type = "release", ReleaseDate = new DateTime(2024, 6, 13) },
                new() { Id = "1.20.6", Type = "release", ReleaseDate = new DateTime(2024, 4, 29) },
                new() { Id = "1.20.4", Type = "release", ReleaseDate = new DateTime(2023, 12, 7) },
                new() { Id = "1.20.2", Type = "release", ReleaseDate = new DateTime(2023, 9, 21) },
                new() { Id = "1.20.1", Type = "release", ReleaseDate = new DateTime(2023, 6, 12) },
                new() { Id = "1.19.4", Type = "release", ReleaseDate = new DateTime(2023, 3, 14) },
                new() { Id = "1.19.2", Type = "release", ReleaseDate = new DateTime(2022, 8, 5) },
                new() { Id = "1.18.2", Type = "release", ReleaseDate = new DateTime(2022, 2, 28) },
                new() { Id = "1.17.1", Type = "release", ReleaseDate = new DateTime(2021, 7, 6) },
                new() { Id = "1.16.5", Type = "release", ReleaseDate = new DateTime(2021, 1, 15) },
                new() { Id = "1.15.2", Type = "release", ReleaseDate = new DateTime(2020, 1, 21) },
                new() { Id = "1.14.4", Type = "release", ReleaseDate = new DateTime(2019, 7, 19) },
                new() { Id = "1.12.2", Type = "release", ReleaseDate = new DateTime(2017, 9, 18) },
                new() { Id = "1.8.9", Type = "release", ReleaseDate = new DateTime(2015, 12, 9) },
                new() { Id = "1.7.10", Type = "release", ReleaseDate = new DateTime(2014, 6, 26) },
                new() { Id = "b1.7.3", Type = "old_beta", ReleaseDate = new DateTime(2011, 7, 8) },
                new() { Id = "a1.2.6", Type = "old_alpha", ReleaseDate = new DateTime(2010, 12, 3) }
            };
        }

        public async Task<List<LoaderInfo>> GetCompatibleLoadersAsync(string mcVersion)
        {
            var loaders = new List<LoaderInfo>
            {
                new()
                {
                    Name = "Vanilla",
                    Description = "Official Minecraft with no modifications",
                    IsCompatible = true,
                    CompatibilityNote = "Compatible",
                    AvailableVersions = new List<string> { "None" }
                },
                new()
                {
                    Name = "Fabric",
                    Description = "Lightweight, high-performance mod loader",
                    IsCompatible = true,
                    CompatibilityNote = "Compatible",
                    AvailableVersions = new List<string> { "0.16.10", "0.16.9", "0.15.11" }
                },
                new()
                {
                    Name = "Forge",
                    Description = "Traditional modding platform with wide ecosystem",
                    IsCompatible = true,
                    CompatibilityNote = "Compatible",
                    AvailableVersions = new List<string> { "Latest Release", "Recommended" }
                },
                new()
                {
                    Name = "NeoForge",
                    Description = "Next-generation Forge fork for modern Minecraft",
                    IsCompatible = IsNeoForgeCompatible(mcVersion),
                    CompatibilityNote = IsNeoForgeCompatible(mcVersion) ? "Compatible" : "NeoForge requires Minecraft 1.20.2 or newer",
                    AvailableVersions = new List<string> { "Latest Build", "Recommended" }
                },
                new()
                {
                    Name = "Quilt",
                    Description = "Community-driven modular mod loader (Fabric compatible)",
                    IsCompatible = true,
                    CompatibilityNote = "Compatible",
                    AvailableVersions = new List<string> { "0.26.0", "0.25.0" }
                }
            };

            return await Task.FromResult(loaders);
        }

        private static bool IsNeoForgeCompatible(string mcVersion)
        {
            if (string.IsNullOrWhiteSpace(mcVersion)) return false;
            if (mcVersion.StartsWith("26.") || mcVersion.StartsWith("25.")) return true;

            var parts = mcVersion.Split('.');
            if (parts.Length >= 2 && int.TryParse(parts[0], out var major) && int.TryParse(parts[1], out var minor))
            {
                if (major > 1) return true;
                if (major == 1 && minor >= 21) return true;
                if (major == 1 && minor == 20)
                {
                    if (parts.Length >= 3 && int.TryParse(parts[2], out var patch))
                    {
                        return patch >= 2;
                    }
                }
            }
            return false;
        }

        public async Task<List<string>> GetLoaderVersionsAsync(string loaderName, string mcVersion)
        {
            if (loaderName.Equals("Vanilla", StringComparison.OrdinalIgnoreCase))
            {
                return new List<string> { "Default" };
            }

            if (loaderName.Equals("Fabric", StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    var res = await _http.GetAsync($"https://meta.fabricmc.net/v2/versions/loader/{mcVersion}");
                    if (res.IsSuccessStatusCode)
                    {
                        var json = await res.Content.ReadAsStringAsync();
                        var doc = JsonDocument.Parse(json);
                        var list = new List<string>();
                        foreach (var elem in doc.RootElement.EnumerateArray())
                        {
                            if (elem.TryGetProperty("loader", out var loaderProp) && loaderProp.TryGetProperty("version", out var vProp))
                            {
                                if (vProp.GetString() is string v) list.Add(v);
                            }
                        }
                        if (list.Count > 0) return list.Take(10).ToList();
                    }
                }
                catch { }

                return new List<string> { "0.16.10", "0.16.9", "0.16.5", "0.15.11" };
            }

            if (loaderName.Equals("Forge", StringComparison.OrdinalIgnoreCase))
            {
                return new List<string> { "51.0.33", "50.1.0", "47.2.20", "40.2.14" };
            }

            if (loaderName.Equals("NeoForge", StringComparison.OrdinalIgnoreCase))
            {
                return new List<string> { "21.1.84", "20.4.80", "20.2.86" };
            }

            if (loaderName.Equals("Quilt", StringComparison.OrdinalIgnoreCase))
            {
                return new List<string> { "0.26.0", "0.25.0", "0.24.0" };
            }

            return new List<string> { "Latest Recommended", "Latest Build" };
        }

        public async Task<List<ModpackInfo>> SearchModrinthModpacksAsync(string query, string mcVersion, string loader)
        {
            var results = new List<ModpackInfo>
            {
                new ModpackInfo
                {
                    Id = "none",
                    Title = "NO MODPACK / VANILLA",
                    Description = "Create a clean instance without pre-installed modpacks",
                    Author = "VayuClient",
                    LatestVersion = "None",
                    IsCompatible = true
                }
            };

            try
            {
                string loaderFacet = loader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase) ? "" : $",[\"categories:{loader.ToLowerInvariant()}\"]";
                string url = $"https://api.modrinth.com/v2/search?query={Uri.EscapeDataString(query)}&facets=[[\"project_type:modpack\"],[\"versions:{Uri.EscapeDataString(mcVersion)}\"]{loaderFacet}]&limit=12";

                var response = await _http.GetAsync(url);
                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    var doc = JsonDocument.Parse(json);
                    if (doc.RootElement.TryGetProperty("hits", out var hitsElem))
                    {
                        foreach (var hit in hitsElem.EnumerateArray())
                        {
                            var id = hit.GetProperty("project_id").GetString() ?? "";
                            var title = hit.GetProperty("title").GetString() ?? "";
                            var desc = hit.GetProperty("description").GetString() ?? "";
                            var author = hit.GetProperty("author").GetString() ?? "";
                            var iconUrl = hit.TryGetProperty("icon_url", out var iconProp) ? iconProp.GetString() ?? "" : "";

                            var compVersions = new List<string>();
                            if (hit.TryGetProperty("versions", out var vArray))
                            {
                                foreach (var v in vArray.EnumerateArray())
                                    if (v.GetString() is string vStr) compVersions.Add(vStr);
                            }

                            var compLoaders = new List<string>();
                            if (hit.TryGetProperty("categories", out var cArray))
                            {
                                foreach (var c in cArray.EnumerateArray())
                                    if (c.GetString() is string cStr) compLoaders.Add(cStr);
                            }

                            bool isComp = compVersions.Contains(mcVersion) || compVersions.Count == 0;
                            string warn = isComp ? "" : $"Incompatible: Required version not matching {mcVersion}";

                            results.Add(new ModpackInfo
                            {
                                Id = id,
                                Title = title,
                                Description = desc,
                                Author = author,
                                IconUrl = iconUrl,
                                CompatibleVersions = compVersions,
                                CompatibleLoaders = compLoaders,
                                IsCompatible = isComp,
                                CompatibilityWarning = warn
                            });
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Modrinth search API error: {ex.Message}");
            }

            return results;
        }
    }
}
