using System;
using System.Collections.Generic;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using Microsoft.AspNetCore.SignalR.Client;

namespace VayuClient.Services.Backend
{
    // ─── Presence DTOs ──────────────────────────────────────────────────────────

    public sealed record OnlinePlayerEntry(string Username, string AccountType, string Status);

    public sealed record PresenceSnapshot(int OnlineCount, IReadOnlyList<OnlinePlayerEntry> Players, string Timestamp);

    // ─── Identity context passed in by the caller ────────────────────────────────

    public sealed class PresenceIdentity
    {
        public string Username    { get; init; } = string.Empty;
        public string AccountType { get; init; } = "offline"; // "microsoft" | "offline"
    }

    /// <summary>
    /// Persistent SignalR WebSocket connection to the VayuClient production backend hub.
    ///
    /// Provides real-time push events:
    ///   – Server status updates
    ///   – New release published
    ///   – Announcement broadcasts
    ///   – Maintenance mode toggle
    ///   – Player presence changes (PLAYER_ONLINE / PLAYER_OFFLINE)
    ///
    /// Reconnects automatically with exponential backoff.
    /// Re-joins presence automatically after every reconnect.
    /// Never throws to callers — fully offline-safe.
    /// </summary>
    public sealed class SignalRClientService : IAsyncDisposable
    {
        private const string HubUrl = "https://vayu.rencloud.online/hubs/vayu";
        private const int MaxReconnectDelaySec = 60;

        // Shared key used for HMAC-SHA256 presence token.
        // Derived from the same secret embedded in the launcher.
        // This prevents casual spoofing (e.g. arbitrary curl requests).
        // It is not the Microsoft token — no auth secrets cross the wire.
        private const string PresenceSharedKey = "VayuLauncher-PresenceKey-2026";

        private HubConnection? _connection;
        private CancellationTokenSource _cts = new();
        private bool _disposed;

        // The current authenticated identity — set before connecting
        private PresenceIdentity? _identity;
        // Stable session ID for this launcher process (random, non-sensitive)
        private readonly string _sessionId = Guid.NewGuid().ToString("N")[..16];

        // ── Events ─────────────────────────────────────────────────────────────

        /// <summary>Server status changed: (serverId, isOnline, onlinePlayers, pingMs)</summary>
        public event Action<Guid, bool, int, int>? ServerStatusChanged;

        /// <summary>New release published: versionTag</summary>
        public event Action<string>? NewReleasePublished;

        /// <summary>Announcement broadcast: (title, message, level)</summary>
        public event Action<string, string, string>? AnnouncementBroadcast;

        /// <summary>Maintenance mode toggled: isActive</summary>
        public event Action<bool>? MaintenanceModeToggled;

        /// <summary>SignalR connection state changed: isConnected</summary>
        public event Action<bool>? ConnectionStateChanged;

        // ── Presence events ────────────────────────────────────────────────────

        /// <summary>
        /// A new unique VayuClient user came online.
        /// Data source: live backend — never fabricated.
        /// </summary>
        public event Action<string, string>? PlayerOnline;     // (username, accountType)

        /// <summary>
        /// A VayuClient user went offline (last connection closed/timed-out).
        /// Data source: live backend — never fabricated.
        /// </summary>
        public event Action<string>? PlayerOffline;            // (username)

        /// <summary>Received a friend request: fromUsername</summary>
        public event Action<string>? FriendRequestReceived;

        /// <summary>A friend request was accepted: byUsername</summary>
        public event Action<string>? FriendRequestAccepted;

        /// <summary>A friend request was declined: byUsername</summary>
        public event Action<string>? FriendRequestDeclined;

        /// <summary>Friend status or current server changed: (username, isOnline, currentServer)</summary>
        public event Action<string, bool, string?>? FriendStatusChanged;

        /// <summary>Received a server invite: (fromUsername, serverIp, serverName, timestamp)</summary>
        public event Action<string, string, string, long>? ServerInviteReceived;

        /// <summary>
        /// Full presence snapshot pushed by the server after any state change.
        /// Contains the real current list of connected users.
        /// </summary>
        public event Action<PresenceSnapshot>? PresenceSnapshotReceived;

        public bool IsConnected => _connection?.State == HubConnectionState.Connected;

        // ── Lifecycle ──────────────────────────────────────────────────────────

        public async Task StartAsync(PresenceIdentity? identity = null)
        {
            if (_disposed) return;
            _identity = identity;

            _connection = new HubConnectionBuilder()
                .WithUrl(HubUrl)
                .WithAutomaticReconnect(new[] { 0, 2, 5, 10, 20, 30, MaxReconnectDelaySec }
                    .Select(d => TimeSpan.FromSeconds(d)).ToArray())
                .Build();

            RegisterHandlers();

            _connection.Reconnecting += _ =>
            {
                DispatchConnectionState(false);
                return Task.CompletedTask;
            };

            _connection.Reconnected += async _ =>
            {
                DispatchConnectionState(true);
                // Re-join presence after every reconnect so the user doesn't
                // disappear from the online list on network blips
                await JoinPresenceAsync().ConfigureAwait(false);
            };

            _connection.Closed += ex =>
            {
                DispatchConnectionState(false);
                if (!_disposed) { var __ = ConnectWithBackoffAsync(_cts.Token); }
                return Task.CompletedTask;
            };

            await ConnectWithBackoffAsync(_cts.Token).ConfigureAwait(false);
        }

        public async Task StopAsync()
        {
            if (_connection != null)
            {
                await _connection.StopAsync().ConfigureAwait(false);
            }
        }

        /// <summary>
        /// Update the active identity (e.g. after login/logout) and re-register presence.
        /// Safe to call at any time — no-ops if not connected.
        /// </summary>
        public async Task UpdatePresenceIdentityAsync(PresenceIdentity? identity)
        {
            _identity = identity;
            if (IsConnected)
            {
                await JoinPresenceAsync().ConfigureAwait(false);
            }
        }

        // ── Private ────────────────────────────────────────────────────────────

        private void RegisterHandlers()
        {
            if (_connection == null) return;

            _connection.On<Guid, bool, int, int>("ServerStatusChanged", (id, online, players, ping) =>
                Dispatch(() => ServerStatusChanged?.Invoke(id, online, players, ping)));

            _connection.On<string>("NewReleasePublished", tag =>
                Dispatch(() => NewReleasePublished?.Invoke(tag)));

            _connection.On<string, string, string>("AnnouncementBroadcast", (title, msg, level) =>
                Dispatch(() => AnnouncementBroadcast?.Invoke(title, msg, level)));

            _connection.On<bool>("MaintenanceModeToggled", active =>
                Dispatch(() => MaintenanceModeToggled?.Invoke(active)));

            // ── Presence handlers ────────────────────────────────────────────
            // Data origin: server-side PresenceService — never client-fabricated.

            _connection.On<string, string>("PlayerOnline", (username, accountType) =>
                Dispatch(() => PlayerOnline?.Invoke(username, accountType)));

            _connection.On<string>("PlayerOffline", username =>
                Dispatch(() => PlayerOffline?.Invoke(username)));

            // ── Friend handlers ──────────────────────────────────────────────
            _connection.On<string>("FriendRequestReceived", from =>
                Dispatch(() => FriendRequestReceived?.Invoke(from)));

            _connection.On<string>("FriendRequestAccepted", by =>
                Dispatch(() => FriendRequestAccepted?.Invoke(by)));

            _connection.On<string>("FriendRequestDeclined", by =>
                Dispatch(() => FriendRequestDeclined?.Invoke(by)));

            _connection.On<string, bool, string?>("FriendStatusChanged", (user, online, srv) =>
                Dispatch(() => FriendStatusChanged?.Invoke(user, online, srv)));

            _connection.On<string, string, string, long>("ServerInviteReceived", (from, ip, name, time) =>
                Dispatch(() => ServerInviteReceived?.Invoke(from, ip, name, time)));

            _connection.On<System.Text.Json.JsonElement>("PresenceSnapshot", raw =>
            {
                try
                {
                    int count = raw.GetProperty("onlineCount").GetInt32();
                    string ts  = raw.TryGetProperty("timestamp", out var tsProp)
                        ? tsProp.GetString() ?? string.Empty
                        : string.Empty;

                    var players = new List<OnlinePlayerEntry>();
                    if (raw.TryGetProperty("players", out var arr))
                    {
                        foreach (var el in arr.EnumerateArray())
                        {
                            string? uname = el.TryGetProperty("username",    out var u) ? u.GetString() : null;
                            string? atype = el.TryGetProperty("accountType", out var a) ? a.GetString() : null;
                            string? stat  = el.TryGetProperty("status",      out var s) ? s.GetString() : null;
                            if (uname != null)
                                players.Add(new OnlinePlayerEntry(uname, atype ?? "offline", stat ?? "online"));
                        }
                    }

                    var snapshot = new PresenceSnapshot(count, players, ts);
                    Dispatch(() => PresenceSnapshotReceived?.Invoke(snapshot));
                }
                catch { /* Ignore malformed snapshot */ }
            });
        }

        /// <summary>
        /// Sends JoinPresence to the hub with an HMAC-signed token.
        ///
        /// What we send:   username, accountType, sessionId, unixTimestamp, hmacSignature
        /// What we DON'T: Microsoft access token, refresh token, email, UUID, hardware info
        ///
        /// The signature prevents arbitrary curl clients from injecting usernames.
        /// The server derives identity from this validated token, never from raw string input alone.
        /// </summary>
        private async Task JoinPresenceAsync()
        {
            if (_connection == null || !IsConnected || _identity == null) return;
            if (string.IsNullOrWhiteSpace(_identity.Username))             return;

            try
            {
                long   timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds();
                string payload   = $"{_identity.Username}|{_identity.AccountType}|{_sessionId}|{timestamp}";
                string signature = ComputeHmac(payload, PresenceSharedKey);

                await _connection.InvokeAsync(
                    "JoinPresence",
                    _identity.Username,
                    _identity.AccountType,
                    _sessionId,
                    timestamp,
                    signature
                ).ConfigureAwait(false);
            }
            catch (Exception ex)
            {
                // Log but don't crash — presence is non-critical
                System.Diagnostics.Debug.WriteLine($"[SignalR] JoinPresence failed: {ex.Message}");
            }
        }

        private async Task ConnectWithBackoffAsync(CancellationToken ct)
        {
            int delay = 0;
            while (!ct.IsCancellationRequested && _connection != null)
            {
                try
                {
                    if (delay > 0) await Task.Delay(TimeSpan.FromSeconds(delay), ct).ConfigureAwait(false);
                    await _connection.StartAsync(ct).ConfigureAwait(false);
                    DispatchConnectionState(true);

                    // Immediately register presence after a successful initial connect
                    await JoinPresenceAsync().ConfigureAwait(false);
                    return;
                }
                catch (OperationCanceledException)
                {
                    return;
                }
                catch
                {
                    // VPS unreachable — launcher continues working offline
                    DispatchConnectionState(false);
                    delay = Math.Min(delay == 0 ? 5 : delay * 2, MaxReconnectDelaySec);
                }
            }
        }

        /// <summary>
        /// HMAC-SHA256 using the shared presence key.
        /// Returns a lowercase hex string.
        /// Mirrors the server-side VerifyHmac in VayuHub — same derivation.
        /// </summary>
        private static string ComputeHmac(string payload, string sharedKey)
        {
            using var derive = new Rfc2898DeriveBytes(
                sharedKey, Encoding.UTF8.GetBytes("VayuPresenceKey"), 1000, HashAlgorithmName.SHA256);
            byte[] key = derive.GetBytes(32);
            using var hmac = new HMACSHA256(key);
            byte[] hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(payload));
            return Convert.ToHexString(hash).ToLowerInvariant();
        }

        private static void Dispatch(Action action)
        {
            if (Application.Current?.Dispatcher.CheckAccess() == true)
                action();
            else
                Application.Current?.Dispatcher.BeginInvoke(action);
        }

        private void DispatchConnectionState(bool connected) =>
            Dispatch(() => ConnectionStateChanged?.Invoke(connected));

        // ── IAsyncDisposable ───────────────────────────────────────────────────

        public async ValueTask DisposeAsync()
        {
            if (_disposed) return;
            _disposed = true;
            _cts.Cancel();
            _cts.Dispose();
            if (_connection != null)
            {
                await _connection.DisposeAsync().ConfigureAwait(false);
            }
        }
    }
}
