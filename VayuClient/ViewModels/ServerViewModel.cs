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
using VayuClient.Services.Server;

namespace VayuClient.ViewModels
{
    public partial class ServerViewModel : ObservableObject, IDisposable
    {
        private readonly IServerService _serverService;
        private CancellationTokenSource? _pingCts;
        private bool _disposed;

        // ─── Observable State ─────────────────────────────────────────────────

        [ObservableProperty]
        private ObservableCollection<ServerInfo> _servers = new();

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(HasSelection))]
        private ServerInfo? _selectedServer;

        [ObservableProperty]
        private bool _isAddingServer;

        [ObservableProperty]
        private bool _isPingingAll;

        // Add-server form fields
        [ObservableProperty]
        private string _newServerName = string.Empty;

        [ObservableProperty]
        private string _newServerAddress = string.Empty;

        [ObservableProperty]
        private int _newServerPort = 25565;

        [ObservableProperty]
        private string _statusMessage = string.Empty;

        public bool HasSelection => SelectedServer != null;

        public ServerViewModel()
        {
            _serverService = ServiceLocator.Resolve<IServerService>();
            LoadServers();
        }

        // ─── Navigation Lifecycle ─────────────────────────────────────────────

        public void OnNavigatedTo()
        {
            LoadServers();
        }

        public void OnNavigatedFrom()
        {
            CancelActivePings();
            IsAddingServer = false;
        }

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
            finally
            {
                IsPingingAll = false;
            }
        }

        // ─── Commands ─────────────────────────────────────────────────────────

        [RelayCommand]
        public void LoadServers()
        {
            try
            {
                var list = _serverService.GetServers();
                var sorted = list.OrderByDescending(s => s.IsFavorite).ThenBy(s => s.Name).ToList();
                Servers = new ObservableCollection<ServerInfo>(sorted);

                if (Servers.Count == 0)
                {
                    StatusMessage = "No servers saved. Click '＋ Add Server' to add a Minecraft server.";
                }
                else
                {
                    StatusMessage = $"{Servers.Count} server(s) loaded.";
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ServerViewModel.LoadServers", ex, "Servers");
                Servers = new ObservableCollection<ServerInfo>();
                StatusMessage = "Could not load saved servers.";
            }
        }

        [RelayCommand]
        private void BeginAddServer()
        {
            NewServerName = "";
            NewServerAddress = "";
            NewServerPort = 25565;
            IsAddingServer = true;
        }

        [RelayCommand]
        private void CancelAddServer()
        {
            IsAddingServer = false;
            NewServerName = "";
            NewServerAddress = "";
            NewServerPort = 25565;
        }

        [RelayCommand]
        private async Task ConfirmAddServer()
        {
            var rawAddress = NewServerAddress?.Trim() ?? string.Empty;
            if (string.IsNullOrWhiteSpace(rawAddress))
            {
                StatusMessage = "Server address cannot be empty.";
                return;
            }

            // Extract port if formatted as host:port
            string host = rawAddress;
            int port = NewServerPort is > 0 and <= 65535 ? NewServerPort : 25565;
            if (rawAddress.Contains(':') && !rawAddress.StartsWith("["))
            {
                var parts = rawAddress.Split(':');
                host = parts[0];
                if (parts.Length > 1 && int.TryParse(parts[1], out int parsedPort) && parsedPort is > 0 and <= 65535)
                {
                    port = parsedPort;
                }
            }

            var server = new ServerInfo
            {
                Name = string.IsNullOrWhiteSpace(NewServerName) ? host : NewServerName.Trim(),
                Address = host,
                Port = port,
                Status = ServerPingStatus.Unknown,
                PingMs = -1
            };

            try
            {
                await _serverService.AddServerAsync(server);
                IsAddingServer = false;
                LoadServers();
                StatusMessage = $"Server '{server.Name}' added.";

                // Immediately ping the new server asynchronously
                _ = PingSingleAsync(server);
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ServerViewModel.ConfirmAddServer", ex, "Servers");
                StatusMessage = $"Error adding server: {ex.Message}";
            }
        }

        [RelayCommand]
        private async Task RefreshAll()
        {
            if (IsPingingAll || Servers.Count == 0) return;

            IsPingingAll = true;
            StatusMessage = "Pinging all servers...";
            CancelActivePings();
            _pingCts = new CancellationTokenSource();

            try
            {
                await _serverService.PingAllServersAsync(Servers, _pingCts.Token);
                StatusMessage = $"Refreshed {Servers.Count} server(s).";
            }
            catch (OperationCanceledException)
            {
                StatusMessage = "Ping operation cancelled.";
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ServerViewModel.RefreshAll", ex, "Servers");
                StatusMessage = $"Ping error: {ex.Message}";
            }
            finally
            {
                IsPingingAll = false;
            }
        }

        [RelayCommand]
        private async Task PingServer(ServerInfo? server)
        {
            if (server == null) return;
            await PingSingleAsync(server);
        }

        private async Task PingSingleAsync(ServerInfo server)
        {
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(5));
            try
            {
                bool ok = await _serverService.PingServerAsync(server, cts.Token);
                StatusMessage = ok
                    ? $"{server.Name} — {server.PingMs} ms, {server.OnlinePlayers}/{server.MaxPlayers} players"
                    : $"{server.Name} — Offline / Unreachable";
            }
            catch (OperationCanceledException)
            {
                StatusMessage = $"{server.Name} — Timed out.";
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ServerViewModel.PingSingleAsync", ex, "Servers");
                StatusMessage = $"Ping error: {ex.Message}";
            }
        }

        [RelayCommand]
        private async Task ToggleFavorite(ServerInfo? server)
        {
            if (server == null) return;
            try
            {
                server.IsFavorite = !server.IsFavorite;
                await _serverService.UpdateServerAsync(server);
                LoadServers();
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ServerViewModel.ToggleFavorite", ex, "Servers");
            }
        }

        [RelayCommand]
        private async Task DeleteServer(ServerInfo? server)
        {
            if (server == null) return;
            var result = MessageBox.Show(
                $"Remove '{server.Name}' ({server.AddressDisplay}) from your server list?",
                "Remove Server",
                MessageBoxButton.YesNo,
                MessageBoxImage.Question);
            if (result != MessageBoxResult.Yes) return;

            try
            {
                await _serverService.DeleteServerAsync(server.Id);
                Servers.Remove(server);
                if (SelectedServer?.Id == server.Id) SelectedServer = null;
                StatusMessage = $"'{server.Name}' removed.";
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ServerViewModel.DeleteServer", ex, "Servers");
                StatusMessage = $"Error removing server: {ex.Message}";
            }
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

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
            CancelActivePings();
        }
    }
}
