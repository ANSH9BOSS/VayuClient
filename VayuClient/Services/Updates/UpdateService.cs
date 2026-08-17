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
    public enum UpdateState
    {
        Idle,
        Checking,
        UpToDate,
        UpdateAvailable,
        Downloading,
        Verifying,
        Installing,
        Restarting,
        Failed
    }

    public class UpdateCheckResult
    {
        public bool IsUpdateAvailable { get; set; }
        public string CurrentVersion { get; set; } = string.Empty;
        public string LatestVersion { get; set; } = string.Empty;
        public string ReleaseTitle { get; set; } = string.Empty;
        public string ReleaseNotes { get; set; } = string.Empty;
        public string DownloadUrl { get; set; } = string.Empty;
        public string AssetName { get; set; } = string.Empty;
        public long FileSizeBytes { get; set; }
        public DateTime? PublishedAt { get; set; }
        public string HtmlUrl { get; set; } = string.Empty;
        public string StatusMessage { get; set; } = string.Empty;
    }

    public interface IUpdateService
    {
        UpdateState CurrentState { get; }
        UpdateCheckResult? LatestUpdateInfo { get; }
        event Action<UpdateState, string>? StateChanged;
        event Action<UpdateCheckResult>? UpdateAvailable;

        Task<UpdateCheckResult> CheckForUpdatesAsync(bool force = false, CancellationToken ct = default);
        Task<bool> DownloadAndInstallUpdateAsync(UpdateCheckResult updateInfo, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default);
    }

    public class UpdateService : IUpdateService
    {
        private const string GitHubRepo = "ANSH9BOSS/VayuClient";
        private readonly HttpClient _http;
        private readonly IDownloadService _downloadService;
        private UpdateCheckResult? _latestUpdateInfo;
        private DateTime _lastCheckTime = DateTime.MinValue;
        private UpdateState _currentState = UpdateState.Idle;

        public UpdateState CurrentState => _currentState;
        public UpdateCheckResult? LatestUpdateInfo => _latestUpdateInfo;
        public event Action<UpdateState, string>? StateChanged;
        public event Action<UpdateCheckResult>? UpdateAvailable;

        public UpdateService()
        {
            _downloadService = ServiceLocator.Resolve<IDownloadService>();
            _http = new HttpClient
            {
                Timeout = TimeSpan.FromSeconds(10)
            };
            _http.DefaultRequestHeaders.Add("User-Agent", AppInfo.UserAgent);
            _http.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github.v3+json"));

            var token = Environment.GetEnvironmentVariable("GITHUB_TOKEN");
            if (!string.IsNullOrEmpty(token))
            {
                _http.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", token);
            }
        }

        private void SetState(UpdateState state, string message = "")
        {
            _currentState = state;
            StateChanged?.Invoke(state, message);
        }

        public async Task<UpdateCheckResult> CheckForUpdatesAsync(bool force = false, CancellationToken ct = default)
        {
            if (!force && _latestUpdateInfo != null && (DateTime.UtcNow - _lastCheckTime).TotalMinutes < 30)
            {
                return _latestUpdateInfo;
            }

            SetState(UpdateState.Checking, "Connecting to GitHub Releases...");
            CrashLogger.LogMessage($"[AutoUpdate] Current version: {AppInfo.VersionString}");
            CrashLogger.LogMessage("[AutoUpdate] Checking GitHub Releases...");

            var result = new UpdateCheckResult
            {
                CurrentVersion = AppInfo.VersionString
            };

            try
            {
                _lastCheckTime = DateTime.UtcNow;
                string apiUrl = $"https://api.github.com/repos/{GitHubRepo}/releases";
                using var request = new HttpRequestMessage(HttpMethod.Get, apiUrl);
                using var response = await _http.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, ct);

                if (response.StatusCode == System.Net.HttpStatusCode.Forbidden)
                {
                    string msg = "GitHub API rate limit reached. Please try again later.";
                    CrashLogger.LogMessage($"[AutoUpdate] Check failed: {msg}");
                    result.IsUpdateAvailable = false;
                    result.StatusMessage = msg;
                    SetState(UpdateState.Failed, msg);
                    _latestUpdateInfo = result;
                    return result;
                }

                if (!response.IsSuccessStatusCode)
                {
                    string msg = $"GitHub API returned status code {(int)response.StatusCode} ({response.ReasonPhrase})";
                    CrashLogger.LogMessage($"[AutoUpdate] Check failed: {msg}");
                    result.IsUpdateAvailable = false;
                    result.StatusMessage = msg;
                    SetState(UpdateState.Failed, msg);
                    _latestUpdateInfo = result;
                    return result;
                }

                var json = await response.Content.ReadAsStringAsync(ct);
                using var doc = JsonDocument.Parse(json);

                if (doc.RootElement.ValueKind != JsonValueKind.Array || doc.RootElement.GetArrayLength() == 0)
                {
                    string msg = "No releases found on GitHub repository.";
                    CrashLogger.LogMessage($"[AutoUpdate] {msg}");
                    result.IsUpdateAvailable = false;
                    result.StatusMessage = msg;
                    result.LatestVersion = AppInfo.VersionString;
                    SetState(UpdateState.UpToDate, "Running latest version.");
                    _latestUpdateInfo = result;
                    return result;
                }

                // Iterate releases to find the latest valid non-draft, non-prerelease
                JsonElement? bestRelease = null;
                string bestTag = "";

                foreach (var rel in doc.RootElement.EnumerateArray())
                {
                    bool isDraft = rel.TryGetProperty("draft", out var dProp) && dProp.GetBoolean();
                    bool isPre = rel.TryGetProperty("prerelease", out var pProp) && pProp.GetBoolean();
                    if (isDraft || isPre) continue;

                    string tag = rel.TryGetProperty("tag_name", out var tProp) ? tProp.GetString() ?? "" : "";
                    if (!string.IsNullOrWhiteSpace(tag))
                    {
                        bestRelease = rel;
                        bestTag = tag;
                        break;
                    }
                }

                if (!bestRelease.HasValue)
                {
                    result.IsUpdateAvailable = false;
                    result.LatestVersion = AppInfo.VersionString;
                    result.StatusMessage = "No stable release published.";
                    SetState(UpdateState.UpToDate, "Up to date.");
                    _latestUpdateInfo = result;
                    return result;
                }

                var releaseElem = bestRelease.Value;
                string cleanLatestTag = bestTag.TrimStart('v', 'V').Trim();
                string releaseName = releaseElem.TryGetProperty("name", out var nameProp) ? nameProp.GetString() ?? "" : bestTag;
                string body = releaseElem.TryGetProperty("body", out var bodyProp) ? bodyProp.GetString() ?? "" : "";
                string htmlUrl = releaseElem.TryGetProperty("html_url", out var htmlProp) ? htmlProp.GetString() ?? "" : "";

                DateTime? pubDate = null;
                if (releaseElem.TryGetProperty("published_at", out var pubProp) && pubProp.TryGetDateTime(out var dt))
                {
                    pubDate = dt;
                }

                result.LatestVersion = cleanLatestTag;
                result.ReleaseTitle = releaseName;
                result.ReleaseNotes = body;
                result.PublishedAt = pubDate;
                result.HtmlUrl = htmlUrl;

                CrashLogger.LogMessage($"[AutoUpdate] Latest release: v{cleanLatestTag}");

                // Compare semantic versions
                if (IsNewerVersion(cleanLatestTag, AppInfo.VersionString))
                {
                    CrashLogger.LogMessage("[AutoUpdate] Update required");

                    // Robust Asset Resolution: search for Windows installer .exe
                    (string Name, string Url, long Size) selectedAsset = default;
                    int bestScore = -1;

                    if (releaseElem.TryGetProperty("assets", out var assetsArray) && assetsArray.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var asset in assetsArray.EnumerateArray())
                        {
                            string assetName = asset.TryGetProperty("name", out var aName) ? aName.GetString() ?? "" : "";
                            string downloadUrl = asset.TryGetProperty("browser_download_url", out var dUrl) ? dUrl.GetString() ?? "" : "";
                            long size = asset.TryGetProperty("size", out var sProp) ? sProp.GetInt64() : 0;

                            if (!assetName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase) || string.IsNullOrEmpty(downloadUrl))
                            {
                                continue;
                            }

                            int score = 0;
                            if (assetName.Equals("VayuClientSetup.exe", StringComparison.OrdinalIgnoreCase)) score = 100;
                            else if (assetName.Contains("Setup", StringComparison.OrdinalIgnoreCase)) score = 80;
                            else if (assetName.Contains("Installer", StringComparison.OrdinalIgnoreCase)) score = 70;
                            else if (assetName.Contains("VayuClient", StringComparison.OrdinalIgnoreCase)) score = 60;
                            else score = 10;

                            if (score > bestScore)
                            {
                                bestScore = score;
                                selectedAsset = (assetName, downloadUrl, size);
                            }
                        }
                    }

                    if (!string.IsNullOrEmpty(selectedAsset.Url))
                    {
                        result.IsUpdateAvailable = true;
                        result.DownloadUrl = selectedAsset.Url;
                        result.AssetName = selectedAsset.Name ?? "";
                        result.FileSizeBytes = selectedAsset.Size;
                        result.StatusMessage = $"Update v{cleanLatestTag} available ({selectedAsset.Name})";

                        CrashLogger.LogMessage($"[AutoUpdate] Found asset: {selectedAsset.Name}");
                        CrashLogger.LogMessage($"[AutoUpdate] Download URL resolved: {selectedAsset.Url}");

                        SetState(UpdateState.UpdateAvailable, result.StatusMessage);
                        _latestUpdateInfo = result;
                        UpdateAvailable?.Invoke(result);
                    }
                    else
                    {
                        result.IsUpdateAvailable = false;
                        result.StatusMessage = $"Update unavailable: GitHub release v{cleanLatestTag} does not contain a compatible Windows installer.";
                        CrashLogger.LogMessage($"[AutoUpdate] {result.StatusMessage}");
                        SetState(UpdateState.Failed, result.StatusMessage);
                        _latestUpdateInfo = result;
                    }
                }
                else
                {
                    result.IsUpdateAvailable = false;
                    result.StatusMessage = $"You are running the latest version of VayuClient (v{AppInfo.VersionString}).";
                    CrashLogger.LogMessage($"[AutoUpdate] Running latest version: v{AppInfo.VersionString}");
                    SetState(UpdateState.UpToDate, result.StatusMessage);
                    _latestUpdateInfo = result;
                }
            }
            catch (HttpRequestException ex)
            {
                string msg = $"Network error checking updates: {ex.Message}";
                CrashLogger.LogMessage($"[AutoUpdate] {msg}");
                result.IsUpdateAvailable = false;
                result.StatusMessage = msg;
                SetState(UpdateState.Failed, msg);
                _latestUpdateInfo = result;
            }
            catch (TaskCanceledException)
            {
                string msg = "Update check timed out.";
                CrashLogger.LogMessage($"[AutoUpdate] {msg}");
                result.IsUpdateAvailable = false;
                result.StatusMessage = msg;
                SetState(UpdateState.Failed, msg);
                _latestUpdateInfo = result;
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("CheckForUpdatesAsync", ex);
                result.IsUpdateAvailable = false;
                result.StatusMessage = $"Update check error: {ex.Message}";
                SetState(UpdateState.Failed, result.StatusMessage);
                _latestUpdateInfo = result;
            }

            return result;
        }

        public async Task<bool> DownloadAndInstallUpdateAsync(UpdateCheckResult updateInfo, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default)
        {
            if (string.IsNullOrEmpty(updateInfo.DownloadUrl))
            {
                string err = $"No download asset URL provided for release v{updateInfo.LatestVersion}.";
                CrashLogger.LogMessage($"[AutoUpdate] Download error: {err}");
                SetState(UpdateState.Failed, err);
                throw new InvalidOperationException(err);
            }

            SetState(UpdateState.Downloading, $"Downloading update v{updateInfo.LatestVersion}...");
            string tempSetupDir = Path.Combine(Path.GetTempPath(), "VayuClientUpdate");
            Directory.CreateDirectory(tempSetupDir);

            string assetFileName = !string.IsNullOrWhiteSpace(updateInfo.AssetName) 
                ? updateInfo.AssetName 
                : $"VayuClientSetup_v{updateInfo.LatestVersion}.exe";
            string installerPath = Path.Combine(tempSetupDir, assetFileName);

            if (File.Exists(installerPath))
            {
                try { File.Delete(installerPath); } catch { }
            }

            var downloadItem = new DownloadItem
            {
                Url = updateInfo.DownloadUrl,
                DestinationPath = installerPath,
                ExpectedSize = updateInfo.FileSizeBytes,
                Category = "InstallerUpdate",
                Description = $"VayuClient Setup v{updateInfo.LatestVersion}"
            };

            CrashLogger.LogMessage($"[AutoUpdate] Download started from {updateInfo.DownloadUrl}");
            bool ok = await _downloadService.DownloadFileAsync(downloadItem, progress, ct);

            if (!ok || !File.Exists(installerPath))
            {
                string err = "Failed to download update installer.";
                CrashLogger.LogMessage($"[AutoUpdate] {err}");
                SetState(UpdateState.Failed, err);
                throw new InvalidOperationException(err);
            }

            // Verification phase
            SetState(UpdateState.Verifying, "Verifying installer package...");
            var fileInfo = new FileInfo(installerPath);
            if (fileInfo.Length < 1024 * 100) // Minimum 100 KB for real executable
            {
                string err = "Downloaded installer file appears corrupted or incomplete.";
                CrashLogger.LogMessage($"[AutoUpdate] {err} (Size: {fileInfo.Length} bytes)");
                SetState(UpdateState.Failed, err);
                throw new InvalidOperationException(err);
            }

            CrashLogger.LogMessage("[AutoUpdate] Download completed");
            CrashLogger.LogMessage("[AutoUpdate] Verification passed");

            // Installation phase
            SetState(UpdateState.Installing, "Starting installer...");
            CrashLogger.LogMessage($"[AutoUpdate] Starting installer: {installerPath}");

            var psi = new ProcessStartInfo
            {
                FileName = installerPath,
                UseShellExecute = true
            };

            Process.Start(psi);

            SetState(UpdateState.Restarting, "Restarting VayuClient...");
            await Task.Delay(600, ct);

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
