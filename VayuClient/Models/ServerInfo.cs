using System;
using CommunityToolkit.Mvvm.ComponentModel;
using Newtonsoft.Json;

namespace VayuClient.Models
{
    /// <summary>
    /// A saved Minecraft Java Edition server entry.
    /// Persistent fields are serialized to %APPDATA%\VayuClient\servers.json.
    /// Runtime-only fields (status, ping, MOTD, favicon) are populated by the
    /// ServerService SLP ping and never written to disk.
    /// </summary>
    public partial class ServerInfo : ObservableObject
    {
        [JsonProperty("id")]
        public string Id { get; set; } = Guid.NewGuid().ToString("N");

        [JsonProperty("name")]
        [ObservableProperty]
        private string _name = "My Server";

        [JsonProperty("address")]
        [ObservableProperty]
        private string _address = "";

        [JsonProperty("port")]
        [ObservableProperty]
        private int _port = 25565;

        [JsonProperty("isFavorite")]
        [ObservableProperty]
        private bool _isFavorite;

        [JsonProperty("addedAt")]
        public DateTime AddedAt { get; set; } = DateTime.UtcNow;

        // --- Runtime-only (not persisted) ---

        [JsonIgnore]
        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(StatusDisplay))]
        [NotifyPropertyChangedFor(nameof(IsOnline))]
        [NotifyPropertyChangedFor(nameof(IsPinging))]
        private ServerPingStatus _status = ServerPingStatus.Unknown;

        [JsonIgnore]
        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(PingDisplay))]
        private int _pingMs = -1;

        [JsonIgnore]
        [ObservableProperty]
        private string _motd = string.Empty;

        [JsonIgnore]
        [ObservableProperty]
        private int _onlinePlayers;

        [JsonIgnore]
        [ObservableProperty]
        private int _maxPlayers;

        [JsonIgnore]
        [ObservableProperty]
        private string _serverVersion = string.Empty;

        [JsonIgnore]
        [ObservableProperty]
        private string? _faviconBase64;

        [JsonIgnore]
        [ObservableProperty]
        private DateTime? _lastPingedAt;

        // --- Computed display helpers ---

        [JsonIgnore]
        public string AddressDisplay => Port != 25565 ? $"{Address}:{Port}" : Address;

        [JsonIgnore]
        public string StatusDisplay => Status switch
        {
            ServerPingStatus.Online  => $"Online - {OnlinePlayers}/{MaxPlayers} players",
            ServerPingStatus.Offline => "Offline",
            ServerPingStatus.Pinging => "Pinging...",
            ServerPingStatus.Unknown => "Unknown",
            _ => "Unknown"
        };

        [JsonIgnore]
        public string PingDisplay => PingMs >= 0 ? $"{PingMs} ms" : "--";

        [JsonIgnore]
        public bool IsOnline => Status == ServerPingStatus.Online;

        [JsonIgnore]
        public bool IsPinging => Status == ServerPingStatus.Pinging;

        public void UpdateRuntimeState(
            ServerPingStatus status,
            int pingMs = -1,
            string? motd = null,
            int onlinePlayers = 0,
            int maxPlayers = 0,
            string? serverVersion = null,
            string? faviconBase64 = null)
        {
            void Apply()
            {
                Status = status;
                PingMs = pingMs;
                if (motd != null) Motd = motd;
                OnlinePlayers = onlinePlayers;
                MaxPlayers = maxPlayers;
                if (serverVersion != null) ServerVersion = serverVersion;
                if (faviconBase64 != null) FaviconBase64 = faviconBase64;
                LastPingedAt = DateTime.Now;

                OnPropertyChanged(nameof(StatusDisplay));
                OnPropertyChanged(nameof(PingDisplay));
                OnPropertyChanged(nameof(IsOnline));
                OnPropertyChanged(nameof(IsPinging));
            }

            var app = System.Windows.Application.Current;
            if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess() && !app.Dispatcher.HasShutdownStarted)
            {
                try
                {
                    app.Dispatcher.BeginInvoke(Apply);
                    return;
                }
                catch { }
            }

            Apply();
        }

        public void NotifyRuntimeChanged()
        {
            var app = System.Windows.Application.Current;
            if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess() && !app.Dispatcher.HasShutdownStarted)
            {
                try
                {
                    app.Dispatcher.BeginInvoke(() =>
                    {
                        OnPropertyChanged(nameof(Status));
                        OnPropertyChanged(nameof(PingMs));
                        OnPropertyChanged(nameof(Motd));
                        OnPropertyChanged(nameof(OnlinePlayers));
                        OnPropertyChanged(nameof(MaxPlayers));
                        OnPropertyChanged(nameof(ServerVersion));
                        OnPropertyChanged(nameof(FaviconBase64));
                        OnPropertyChanged(nameof(StatusDisplay));
                        OnPropertyChanged(nameof(PingDisplay));
                        OnPropertyChanged(nameof(IsOnline));
                        OnPropertyChanged(nameof(IsPinging));
                    });
                    return;
                }
                catch { }
            }
            OnPropertyChanged(nameof(Status));
            OnPropertyChanged(nameof(PingMs));
            OnPropertyChanged(nameof(Motd));
            OnPropertyChanged(nameof(OnlinePlayers));
            OnPropertyChanged(nameof(MaxPlayers));
            OnPropertyChanged(nameof(ServerVersion));
            OnPropertyChanged(nameof(FaviconBase64));
            OnPropertyChanged(nameof(StatusDisplay));
            OnPropertyChanged(nameof(PingDisplay));
            OnPropertyChanged(nameof(IsOnline));
            OnPropertyChanged(nameof(IsPinging));
        }
    }

    public enum ServerPingStatus
    {
        Unknown,
        Pinging,
        Online,
        Offline
    }
}
