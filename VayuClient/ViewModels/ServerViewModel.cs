using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Account;
using VayuClient.Services.Backend;
using VayuClient.Services.Server;

namespace VayuClient.ViewModels
{
    // ─── Simple display model for an online player row ───────────────────────────

    public sealed class OnlinePlayerViewModel : ObservableObject
    {
        private string _username    = string.Empty;
        private string _accountType = "offline";

        public string Username
        {
            get => _username;
            set => SetProperty(ref _username, value);
        }

        public string AccountType
        {
            get => _accountType;
            set => SetProperty(ref _accountType, value);
        }

        /// <summary>True for Microsoft accounts; false for offline/local profiles.</summary>
        public bool IsMicrosoft => string.Equals(_accountType, "microsoft", StringComparison.OrdinalIgnoreCase);
    }

    // ─── ServerViewModel ─────────────────────────────────────────────────────────

    public partial class ServerViewModel : ObservableObject, ILifecycleViewModel
    {
        private readonly IServerService         _serverService;
        private readonly IAccountService?       _accountService;
        private readonly SignalRClientService?  _signalR;
        private CancellationTokenSource? _pingCts;
        private bool _disposed;
        private bool _presenceSubscribed;

        // ─── Minecraft server list ────────────────────────────────────────────

        [ObservableProperty]
        private ObservableCollection<ServerInfo> _servers = new();

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(HasSelection))]
        private ServerInfo? _selectedServer;

        [ObservableProperty] private bool   _isAddingServer;
        [ObservableProperty] private bool   _isPingingAll;
        [ObservableProperty] private string _newServerName    = string.Empty;
        [ObservableProperty] private string _newServerAddress = string.Empty;
        [ObservableProperty] private int    _newServerPort    = 25565;
        [ObservableProperty] private string _statusMessage    = string.Empty;

        public bool HasSelection => SelectedServer != null;

        // ─── VayuClient Network Presence Panel ───────────────────────────────

        /// <summary>Number of unique VayuClient users currently online.</summary>
        [ObservableProperty]
        private int _vayuOnlineCount;

        /// <summary>Live list of online VayuClient users. Source: backend PresenceService.</summary>
        [ObservableProperty]
        private ObservableCollection<OnlinePlayerViewModel> _vayuOnlinePlayers = new();

        /// <summary>
        /// Human-readable connection state shown in the UI.
        /// Possible values: "Connected", "Reconnecting...", "Connection unavailable"
        /// </summary>
        [ObservableProperty]
        private string _vayuNetworkStatus = "Connecting...";

        /// <summary>True while presence data is available (backend reachable).</summary>
        [ObservableProperty]
        private bool _isPresenceAvailable;

        /// <summary>Formatted presence summary, e.g. "3 Players Online".</summary>
        public string VayuPresenceSummary => IsPresenceAvailable
            ? $"{VayuOnlineCount} {(VayuOnlineCount == 1 ? "Player" : "Players")} Online"
            : "Partner server information is currently unavailable.";

        /// <summary>
        /// Last time the presence panel was updated (local time, formatted).
        /// Displayed as "Updated in real time" when empty.
        /// </summary>
        [ObservableProperty]
        private string _presenceLastUpdated = string.Empty;

        // ─── Constructor ──────────────────────────────────────────────────────

        public ServerViewModel()
        {
            _serverService = ServiceLocator.Resolve<IServerService>();

            try { _accountService = ServiceLocator.Resolve<IAccountService>(); } catch { }
            try { _signalR        = ServiceLocator.Resolve<SignalRClientService>(); } catch { }

            SubscribeToPresenceEvents();
            LoadServers();
        }

        // ─── Navigation Lifecycle ─────────────────────────────────────────────

        public Task InitializeAsync()
        {
            LoadServers();
            return Task.CompletedTask;
        }

        public void Activate()
        {
            LoadServers();
            // Re-register presence identity in case user logged in/out since last visit
            _ = Task.Run(UpdatePresenceIdentityAsync);
        }

        public void Deactivate()
        {
            CancelActivePings();
            IsAddingServer = false;
        }

        public void OnNavigatedTo()  => Activate();
        public void OnNavigatedFrom() => Deactivate();

        // ─── Presence Subscription ────────────────────────────────────────────

        private void SubscribeToPresenceEvents()
        {
            if (_signalR == null || _presenceSubscribed) return;
            _presenceSubscribed = true;

            // Connection state
            _signalR.ConnectionStateChanged += connected =>
            {
                Dispatch(() =>
                {
                    IsPresenceAvailable  = connected;
                    VayuNetworkStatus    = connected ? "Connected" : "Reconnecting...";
                    OnPropertyChanged(nameof(VayuPresenceSummary));
                    if (!connected)
                    {
                        // Don't clear the player list immediately — mark it stale instead.
                        // If connection doesn't come back, Reconnecting handler will clear it
                        // after the hub-level ClientTimeoutInterval elapses and a full
                        // PresenceSnapshot is pushed on reconnect.
                    }
                });
            };

            // PLAYER_ONLINE: new unique user connected
            _signalR.PlayerOnline += (username, accountType) =>
            {
                Dispatch(() =>
                {
                    // Idempotent — don't add if already present (rapid connect race)
                    if (!VayuOnlinePlayers.Any(p =>
                        string.Equals(p.Username, username, StringComparison.OrdinalIgnoreCase)))
                    {
                        VayuOnlinePlayers.Add(new OnlinePlayerViewModel
                        {
                            Username    = username,
                            AccountType = accountType
                        });
                    }
                    VayuOnlineCount = VayuOnlinePlayers.Count;
                    OnPropertyChanged(nameof(VayuPresenceSummary));
                    PresenceLastUpdated = DateTime.Now.ToString("HH:mm:ss");
                });
            };

            // PLAYER_OFFLINE: user's last connection closed or timed out
            _signalR.PlayerOffline += username =>
            {
                Dispatch(() =>
                {
                    var existing = VayuOnlinePlayers
                        .FirstOrDefault(p =>
                            string.Equals(p.Username, username, StringComparison.OrdinalIgnoreCase));
                    if (existing != null) VayuOnlinePlayers.Remove(existing);
                    VayuOnlineCount = VayuOnlinePlayers.Count;
                    OnPropertyChanged(nameof(VayuPresenceSummary));
                    PresenceLastUpdated = DateTime.Now.ToString("HH:mm:ss");
                });
            };

            // Full snapshot — authoritative state pushed after reconnect or join
            _signalR.PresenceSnapshotReceived += snapshot =>
            {
                Dispatch(() =>
                {
                    // Rebuild from authoritative server state — don't trust old local list
                    VayuOnlinePlayers.Clear();
                    foreach (var p in snapshot.Players)
                    {
                        VayuOnlinePlayers.Add(new OnlinePlayerViewModel
                        {
                            Username    = p.Username,
                            AccountType = p.AccountType
                        });
                    }
                    VayuOnlineCount      = snapshot.OnlineCount;
                    IsPresenceAvailable  = true;
                    VayuNetworkStatus    = "Connected";
                    OnPropertyChanged(nameof(VayuPresenceSummary));
                    PresenceLastUpdated  = DateTime.Now.ToString("HH:mm:ss");
                });
            };
        }

        private async Task UpdatePresenceIdentityAsync()
        {
            if (_signalR == null || _accountService == null) return;
            try
            {
                var profile = _accountService.ActiveProfile;
                PresenceIdentity? identity = null;
                if (profile != null && !string.IsNullOrWhiteSpace(profile.Username))
                {
                    identity = new PresenceIdentity
                    {
                        // Only the display username — never email, token, or UUID
                        Username    = profile.Username,
                        AccountType = profile.AccountType == AccountType.Microsoft ? "microsoft" : "offline"
                    };
                }
                await _signalR.UpdatePresenceIdentityAsync(identity).ConfigureAwait(false);
            }
            catch { /* Non-critical */ }
        }

        // ─── Commands ─────────────────────────────────────────────────────────

        [RelayCommand]
        public void LoadServers()
        {
            try
            {
                var list   = _serverService.GetServers();
                var sorted = list.OrderByDescending(s => s.IsFavorite).ThenBy(s => s.Name).ToList();
                Servers = new ObservableCollection<ServerInfo>(sorted);
                StatusMessage = Servers.Count == 0
                    ? "No servers saved. Click '＋ Add Server' to add a Minecraft server."
                    : $"{Servers.Count} server(s) loaded.";
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ServerViewModel.LoadServers", ex, "Servers");
                Servers = new ObservableCollection<ServerInfo>();
                StatusMessage = "Could not load saved servers.";
            }
        }

        [RelayCommand] private void BeginAddServer()
        {
            NewServerName    = "";
            NewServerAddress = "";
            NewServerPort    = 25565;
            IsAddingServer   = true;
        }

        [RelayCommand] private void CancelAddServer()
        {
            IsAddingServer   = false;
            NewServerName    = "";
            NewServerAddress = "";
            NewServerPort    = 25565;
        }

        [RelayCommand]
        private void ConfirmAddServer()
        {
            var rawAddress = NewServerAddress?.Trim() ?? string.Empty;
            if (string.IsNullOrWhiteSpace(rawAddress))
            {
                StatusMessage = "Server address cannot be empty.";
                return;
            }

            string host = rawAddress;
            int    port = NewServerPort is > 0 and <= 65535 ? NewServerPort : 25565;
            if (rawAddress.Contains(':') && !rawAddress.StartsWith("["))
            {
                var parts = rawAddress.Split(':');
                host = parts[0];
                if (parts.Length > 1 && int.TryParse(parts[1], out int p) && p is > 0 and <= 65535)
                    port = p;
            }

            var server = new ServerInfo
            {
                Name    = string.IsNullOrWhiteSpace(NewServerName) ? host : NewServerName.Trim(),
                Address = host,
                Port    = port,
                Status  = ServerPingStatus.Unknown,
                PingMs  = -1
            };

            IsAddingServer   = false;
            NewServerName    = "";
            NewServerAddress = "";

            _ = Task.Run(async () =>
            {
                try
                {
                    await _serverService.AddServerAsync(server);
                    Dispatch(() =>
                    {
                        LoadServers();
                        StatusMessage = $"Server '{server.Name}' added.";
                    });
                    await PingSingleAsync(server);
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException("ServerViewModel.ConfirmAddServer", ex, "Servers");
                    Dispatch(() => StatusMessage = $"Error adding server: {ex.Message}");
                }
            });
        }

        [RelayCommand]
        private void RefreshAll()
        {
            if (IsPingingAll || Servers.Count == 0) return;
            IsPingingAll  = true;
            StatusMessage = "Pinging all servers...";
            CancelActivePings();
            _pingCts = new CancellationTokenSource();
            var token      = _pingCts.Token;
            var serverList = Servers.ToList();

            _ = Task.Run(async () =>
            {
                try
                {
                    await _serverService.PingAllServersAsync(serverList, token);
                    Dispatch(() => StatusMessage = $"Refreshed {serverList.Count} server(s).");
                }
                catch (OperationCanceledException)
                {
                    Dispatch(() => StatusMessage = "Ping operation cancelled.");
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException("ServerViewModel.RefreshAll", ex, "Servers");
                    Dispatch(() => StatusMessage = $"Ping error: {ex.Message}");
                }
                finally
                {
                    Dispatch(() => IsPingingAll = false);
                }
            });
        }

        [RelayCommand]
        private void PingServer(ServerInfo? server)
        {
            if (server == null) return;
            _ = Task.Run(async () => await PingSingleAsync(server));
        }

        private async Task PingSingleAsync(ServerInfo server)
        {
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            try
            {
                bool ok = await _serverService.PingServerAsync(server, cts.Token);
                Dispatch(() =>
                {
                    StatusMessage = ok
                        ? $"{server.Name} — {server.PingMs} ms, {server.OnlinePlayers}/{server.MaxPlayers} players"
                        : $"{server.Name} — Offline / Unreachable";
                });
            }
            catch (OperationCanceledException)
            {
                Dispatch(() => StatusMessage = $"{server.Name} — Timed out.");
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ServerViewModel.PingSingleAsync", ex, "Servers");
                Dispatch(() => StatusMessage = $"Ping error: {ex.Message}");
            }
        }

        [RelayCommand]
        private void ToggleFavorite(ServerInfo? server)
        {
            if (server == null) return;
            _ = Task.Run(async () =>
            {
                try
                {
                    server.IsFavorite = !server.IsFavorite;
                    await _serverService.UpdateServerAsync(server);
                    Dispatch(LoadServers);
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException("ServerViewModel.ToggleFavorite", ex, "Servers");
                }
            });
        }

        [RelayCommand]
        private void DeleteServer(ServerInfo? server)
        {
            if (server == null) return;
            var result = MessageBox.Show(
                $"Remove '{server.Name}' ({server.AddressDisplay}) from your server list?",
                "Remove Server", MessageBoxButton.YesNo, MessageBoxImage.Question);
            if (result != MessageBoxResult.Yes) return;

            _ = Task.Run(async () =>
            {
                try
                {
                    await _serverService.DeleteServerAsync(server.Id);
                    Dispatch(() =>
                    {
                        Servers.Remove(server);
                        if (SelectedServer?.Id == server.Id) SelectedServer = null;
                        StatusMessage = $"'{server.Name}' removed.";
                    });
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException("ServerViewModel.DeleteServer", ex, "Servers");
                    Dispatch(() => StatusMessage = $"Error removing server: {ex.Message}");
                }
            });
        }

        [RelayCommand]
        private void CopyAddress(ServerInfo? server)
        {
            if (server == null) return;
            try
            {
                Clipboard.SetText(server.AddressDisplay);
                StatusMessage = $"Copied: {server.AddressDisplay}";
            }
            catch (Exception ex)
            {
                StatusMessage = $"Clipboard error: {ex.Message}";
            }
        }

        // ─── Helpers ──────────────────────────────────────────────────────────

        public void CancelActivePings()
        {
            try
            {
                if (_pingCts != null && !_pingCts.IsCancellationRequested)
                {
                    _pingCts.Cancel();
                    _pingCts.Dispose();
                    _pingCts = null;
                }
            }
            catch { }
            finally { IsPingingAll = false; }
        }

        private static void Dispatch(Action action)
        {
            var app = Application.Current;
            if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess() && !app.Dispatcher.HasShutdownStarted)
            {
                try { app.Dispatcher.BeginInvoke(action); return; } catch { }
            }
            action();
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
            CancelActivePings();
        }
    }
}
