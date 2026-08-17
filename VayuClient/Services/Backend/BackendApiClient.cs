using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading;
using System.Threading.Tasks;

namespace VayuClient.Services.Backend
{
    // ─── DTOs (mirrors backend API contracts) ────────────────────────────────────

    public sealed record BackendHealthDto(
        string Status,
        string Timestamp,
        double DurationMs,
        object? Checks);

    public sealed record ServerDto(
        [property: JsonPropertyName("id")]           Guid   Id,
        [property: JsonPropertyName("name")]         string Name,
        [property: JsonPropertyName("address")]      string Address,
        [property: JsonPropertyName("port")]         int    Port,
        [property: JsonPropertyName("isFeatured")]   bool   IsFeatured,
        [property: JsonPropertyName("motd")]         string? Motd,
        [property: JsonPropertyName("onlinePlayers")] int   OnlinePlayers,
        [property: JsonPropertyName("maxPlayers")]   int    MaxPlayers,
        [property: JsonPropertyName("pingMs")]       int    PingMs,
        [property: JsonPropertyName("isOnline")]     bool   IsOnline,
        [property: JsonPropertyName("version")]      string? Version,
        [property: JsonPropertyName("faviconBase64")] string? FaviconBase64,
        [property: JsonPropertyName("lastPingedAt")] DateTime? LastPingedAt);

    public sealed record ReleaseDto(
        [property: JsonPropertyName("id")]                Guid     Id,
        [property: JsonPropertyName("versionTag")]        string   VersionTag,
        [property: JsonPropertyName("versionName")]       string   VersionName,
        [property: JsonPropertyName("releaseNotes")]      string   ReleaseNotes,
        [property: JsonPropertyName("installerDownloadUrl")] string InstallerDownloadUrl,
        [property: JsonPropertyName("installerSha256")]   string   InstallerSha256,
        [property: JsonPropertyName("installerSizeBytes")] long    InstallerSizeBytes,
        [property: JsonPropertyName("isMandatory")]       bool     IsMandatory,
        [property: JsonPropertyName("publishedAt")]       DateTime PublishedAt);

    public sealed record AnnouncementDto(
        [property: JsonPropertyName("id")]          Guid     Id,
        [property: JsonPropertyName("title")]       string   Title,
        [property: JsonPropertyName("content")]     string   Content,
        [property: JsonPropertyName("level")]       string   Level,
        [property: JsonPropertyName("actionLabel")] string?  ActionLabel,
        [property: JsonPropertyName("actionUrl")]   string?  ActionUrl,
        [property: JsonPropertyName("isActive")]    bool     IsActive,
        [property: JsonPropertyName("startsAt")]    DateTime StartsAt,
        [property: JsonPropertyName("expiresAt")]   DateTime? ExpiresAt);

    public sealed record FeatureFlagsDto(
        [property: JsonPropertyName("maintenanceMode")] bool MaintenanceMode,
        [property: JsonPropertyName("enableCloudSlp")]  bool EnableCloudSlp);

    // ─── API Client ───────────────────────────────────────────────────────────────

    /// <summary>
    /// Lightweight, thread-safe HTTP client for VayuClient's real production backend.
    /// All calls are fully offline-resilient — callers must handle null / exception gracefully.
    /// </summary>
    public sealed class BackendApiClient : IDisposable
    {
        public const string BaseUrl = "https://vayu.rencloud.online";
        public const string FallbackUrl = "http://103.165.11.81:5050";

        private static readonly JsonSerializerOptions _json = new()
        {
            PropertyNameCaseInsensitive = true,
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
        };

        private readonly HttpClient _http;
        private bool _disposed;

        public BackendApiClient()
        {
            _http = new HttpClient
            {
                BaseAddress = new Uri(BaseUrl),
                Timeout = TimeSpan.FromSeconds(10)
            };
            _http.DefaultRequestHeaders.UserAgent.ParseAdd("VayuClient/1.5 (+https://github.com/ANSH9BOSS/VayuClient)");
            _http.DefaultRequestHeaders.Accept.ParseAdd("application/json");
        }

        // ── Public API ─────────────────────────────────────────────────────────

        public Task<bool> IsAliveAsync(CancellationToken ct = default) =>
            SafeGetAsync<bool>(async () =>
            {
                var resp = await _http.GetAsync("/health/live", ct).ConfigureAwait(false);
                return resp.IsSuccessStatusCode;
            }, false);

        public Task<List<ServerDto>?> GetServersAsync(CancellationToken ct = default) =>
            SafeGetAsync(() => GetJsonAsync<List<ServerDto>>("/api/v1/servers", ct));

        public Task<ReleaseDto?> GetLatestReleaseAsync(CancellationToken ct = default) =>
            SafeGetAsync(() => GetJsonAsync<ReleaseDto>("/api/v1/releases/latest", ct));

        public Task<List<ReleaseDto>?> GetAllReleasesAsync(CancellationToken ct = default) =>
            SafeGetAsync(() => GetJsonAsync<List<ReleaseDto>>("/api/v1/releases", ct));

        public Task<List<AnnouncementDto>?> GetAnnouncementsAsync(CancellationToken ct = default) =>
            SafeGetAsync(() => GetJsonAsync<List<AnnouncementDto>>("/api/v1/announcements", ct));

        public Task<FeatureFlagsDto?> GetFeatureFlagsAsync(CancellationToken ct = default) =>
            SafeGetAsync(() => GetJsonAsync<FeatureFlagsDto>("/api/v1/config/flags", ct));

        // ── Helpers ────────────────────────────────────────────────────────────

        private async Task<T?> GetJsonAsync<T>(string path, CancellationToken ct) where T : class
        {
            var resp = await _http.GetAsync(path, ct).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return null;
            var stream = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
            return await JsonSerializer.DeserializeAsync<T>(stream, _json, ct).ConfigureAwait(false);
        }

        private static async Task<T?> SafeGetAsync<T>(Func<Task<T?>> fn, T? fallback = default)
        {
            try
            {
                return await fn().ConfigureAwait(false);
            }
            catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or JsonException or OperationCanceledException)
            {
                // Offline / unreachable — launcher must continue working
                return fallback;
            }
        }

        public void Dispose()
        {
            if (!_disposed)
            {
                _http.Dispose();
                _disposed = true;
            }
        }
    }
}
