using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Download;

namespace VayuClient.Services.Updates
{
    public class UpdateCheckResult
    {
        public bool IsUpdateAvailable { get; set; }
        public string CurrentVersion { get; set; } = string.Empty;
        public string LatestVersion { get; set; } = string.Empty;
        public string ReleaseTitle { get; set; } = string.Empty;
        public string ReleaseNotes { get; set; } = string.Empty;
        public string DownloadUrl { get; set; } = string.Empty;
        public long FileSizeBytes { get; set; }
        public DateTime? PublishedAt { get; set; }
        public string HtmlUrl { get; set; } = string.Empty;
    }

    public interface IUpdateService
    {
        UpdateCheckResult? LatestUpdateInfo { get; }
        event Action<UpdateCheckResult>? UpdateAvailable;

        Task<UpdateCheckResult> CheckForUpdatesAsync(bool force = false);
        Task<bool> DownloadAndInstallUpdateAsync(UpdateCheckResult updateInfo, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default);
    }

    public class UpdateService : IUpdateService
    {
        private const string GitHubRepo = "ANSH9BOSS/VayuClient";
        private readonly HttpClient _http;
        private readonly IDownloadService _downloadService;
        private UpdateCheckResult? _latestUpdateInfo;
        private DateTime _lastCheckTime = DateTime.MinValue;

        public UpdateCheckResult? LatestUpdateInfo => _latestUpdateInfo;
        public event Action<UpdateCheckResult>? UpdateAvailable;

        public UpdateService()
        {
            _downloadService = ServiceLocator.Resolve<IDownloadService>();
            _http = new HttpClient();
            _http.DefaultRequestHeaders.Add("User-Agent", AppInfo.UserAgent);
            _http.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github.v3+json"));

            var token = Environment.GetEnvironmentVariable("GITHUB_TOKEN");
            if (!string.IsNullOrEmpty(token))
            {
                _http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
            }
        }

        public async Task<UpdateCheckResult> CheckForUpdatesAsync(bool force = false)
        {
            if (!force && _latestUpdateInfo != null && (DateTime.UtcNow - _lastCheckTime).TotalMinutes < 30)
            {
                return _latestUpdateInfo;
            }

            var result = new UpdateCheckResult
            {
                CurrentVersion = AppInfo.VersionString
            };

            try
            {
                _lastCheckTime = DateTime.UtcNow;
                string apiUrl = $"https://api.github.com/repos/{GitHubRepo}/releases/latest";
                using var response = await _http.GetAsync(apiUrl);

                JsonDocument? doc = null;
                JsonElement root = default;

                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    doc = JsonDocument.Parse(json);
                    root = doc.RootElement;
                }
                else if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
                {
                    // Fallback to releases list
                    string listUrl = $"https://api.github.com/repos/{GitHubRepo}/releases";
                    using var listRes = await _http.GetAsync(listUrl);
                    if (listRes.IsSuccessStatusCode)
                    {
                        var listJson = await listRes.Content.ReadAsStringAsync();
                        doc = JsonDocument.Parse(listJson);
                        if (doc.RootElement.ValueKind == JsonValueKind.Array && doc.RootElement.GetArrayLength() > 0)
                        {
                            root = doc.RootElement.EnumerateArray().First();
                        }
                    }
                }

                if (doc == null || root.ValueKind != JsonValueKind.Object)
                {
                    CrashLogger.LogMessage($"[Auto-Update]: GitHub releases checked (Status: {response.StatusCode}). You are up to date.");
                    result.IsUpdateAvailable = false;
                    result.LatestVersion = AppInfo.VersionString;
                    _latestUpdateInfo = result;
                    return result;
                }

                string tagName = root.TryGetProperty("tag_name", out var tagProp) ? tagProp.GetString() ?? "" : "";
                string releaseName = root.TryGetProperty("name", out var nameProp) ? nameProp.GetString() ?? "" : tagName;
                string body = root.TryGetProperty("body", out var bodyProp) ? bodyProp.GetString() ?? "" : "";
                string htmlUrl = root.TryGetProperty("html_url", out var htmlProp) ? htmlProp.GetString() ?? "" : "";
                
                DateTime? pubDate = null;
                if (root.TryGetProperty("published_at", out var pubProp) && pubProp.TryGetDateTime(out var dt))
                {
                    pubDate = dt;
                }

                string cleanLatestTag = tagName.TrimStart('v', 'V').Trim();
                if (string.IsNullOrEmpty(cleanLatestTag)) cleanLatestTag = AppInfo.VersionString;

                result.LatestVersion = cleanLatestTag;
                result.ReleaseTitle = releaseName;
                result.ReleaseNotes = body;
                result.PublishedAt = pubDate;
                result.HtmlUrl = htmlUrl;

                // Semantic Version Comparison
                if (IsNewerVersion(cleanLatestTag, AppInfo.VersionString))
                {
                    result.IsUpdateAvailable = true;

                    // Locate VayuClientSetup.exe asset
                    if (root.TryGetProperty("assets", out var assetsArray) && assetsArray.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var asset in assetsArray.EnumerateArray())
                        {
                            string assetName = asset.TryGetProperty("name", out var aName) ? aName.GetString() ?? "" : "";
                            string downloadUrl = asset.TryGetProperty("browser_download_url", out var dUrl) ? dUrl.GetString() ?? "" : "";
                            long size = asset.TryGetProperty("size", out var sProp) ? sProp.GetInt64() : 0;

                            if (assetName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                            {
                                result.DownloadUrl = downloadUrl;
                                result.FileSizeBytes = size;
                                break;
                            }
                        }
                    }

                    _latestUpdateInfo = result;
                    CrashLogger.LogMessage($"[Auto-Update]: New version available: v{result.LatestVersion} (Current: v{result.CurrentVersion})");
                    UpdateAvailable?.Invoke(result);
                }
                else
                {
                    result.IsUpdateAvailable = false;
                    _latestUpdateInfo = result;
                    CrashLogger.LogMessage($"[Auto-Update]: Running latest version v{AppInfo.VersionString}");
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("CheckForUpdatesAsync", ex);
                result.IsUpdateAvailable = false;
                _latestUpdateInfo = result;
            }

            return result;
        }

        public async Task<bool> DownloadAndInstallUpdateAsync(UpdateCheckResult updateInfo, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default)
        {
            if (string.IsNullOrEmpty(updateInfo.DownloadUrl))
            {
                throw new InvalidOperationException("No download asset URL provided for update.");
            }

            string tempSetupDir = Path.Combine(Path.GetTempPath(), "VayuClientUpdate");
            Directory.CreateDirectory(tempSetupDir);
            string installerPath = Path.Combine(tempSetupDir, $"VayuClientSetup_v{updateInfo.LatestVersion}.exe");

            var downloadItem = new DownloadItem
            {
                Url = updateInfo.DownloadUrl,
                DestinationPath = installerPath,
                ExpectedSize = updateInfo.FileSizeBytes,
                Category = "InstallerUpdate",
                Description = $"VayuClient Setup v{updateInfo.LatestVersion}"
            };

            CrashLogger.LogMessage($"[Auto-Update]: Downloading update v{updateInfo.LatestVersion} from {updateInfo.DownloadUrl}...");
            bool ok = await _downloadService.DownloadFileAsync(downloadItem, progress, ct);

            if (!ok || !File.Exists(installerPath))
            {
                throw new InvalidOperationException("Failed to download update installer.");
            }

            CrashLogger.LogMessage($"[Auto-Update]: Launching installer {installerPath}...");

            var psi = new ProcessStartInfo
            {
                FileName = installerPath,
                UseShellExecute = true
            };

            Process.Start(psi);

            await Task.Delay(500, ct);
            var app = Application.Current;
            if (app?.Dispatcher != null)
            {
                app.Dispatcher.Invoke(() => app.Shutdown());
            }

            return true;
        }

        private static bool IsNewerVersion(string latestStr, string currentStr)
        {
            try
            {
                if (System.Version.TryParse(latestStr, out var latest) && System.Version.TryParse(currentStr, out var current))
                {
                    return latest > current;
                }

                var latestParts = latestStr.Split('.').Select(p => int.TryParse(p, out var n) ? n : 0).ToArray();
                var currentParts = currentStr.Split('.').Select(p => int.TryParse(p, out var n) ? n : 0).ToArray();

                int len = Math.Max(latestParts.Length, currentParts.Length);
                for (int i = 0; i < len; i++)
                {
                    int l = i < latestParts.Length ? latestParts[i] : 0;
                    int c = i < currentParts.Length ? currentParts[i] : 0;
                    if (l > c) return true;
                    if (l < c) return false;
                }
            }
            catch { }
            return false;
        }
    }
}
