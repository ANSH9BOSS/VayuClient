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
    public partial class ServerViewModel : ObservableObject
    {
        private readonly IServerService _serverService;
        private CancellationTokenSource? _pingCts;

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

        // ─── Commands ─────────────────────────────────────────────────────────

        [RelayCommand]
        private void LoadServers()
        {
            var list = _serverService.GetServers();
            Servers = new ObservableCollection<ServerInfo>(
                list.OrderByDescending(s => s.IsFavorite).ThenBy(s => s.Name));
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
        private void CancelAddServer() => IsAddingServer = false;

        [RelayCommand]
        private async Task ConfirmAddServer()
        {
            if (string.IsNullOrWhiteSpace(NewServerAddress))
            {
                StatusMessage = "Address cannot be empty.";
                return;
            }

            var server = new ServerInfo
            {
                Name    = string.IsNullOrWhiteSpace(NewServerName) ? NewServerAddress : NewServerName,
                Address = NewServerAddress.Trim(),
                Port    = NewServerPort is > 0 and <= 65535 ? NewServerPort : 25565
            };

            await _serverService.AddServerAsync(server);
            IsAddingServer = false;
            LoadServers();
            StatusMessage = $"Server '{server.Name}' added.";

            // Immediately ping the new server
            _ = PingSingleAsync(server);
        }

        [RelayCommand]
        private async Task RefreshAll()
        {
            if (IsPingingAll) return;
            IsPingingAll = true;
            StatusMessage = "Pinging all servers...";
            _pingCts?.Cancel();
            _pingCts = new CancellationTokenSource();
            try
            {
                await _serverService.PingAllServersAsync(Servers, _pingCts.Token);
                StatusMessage = $"Refreshed {Servers.Count} servers.";
            }
            catch (OperationCanceledException) { StatusMessage = "Ping cancelled."; }
            catch (Exception ex) { StatusMessage = $"Error: {ex.Message}"; }
            finally { IsPingingAll = false; }
        }

        [RelayCommand]
        private async Task PingServer(ServerInfo? server)
        {
            if (server == null) return;
            await PingSingleAsync(server);
        }

        private async Task PingSingleAsync(ServerInfo server)
        {
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(8));
            try
            {
                bool ok = await _serverService.PingServerAsync(server, cts.Token);
                StatusMessage = ok
                    ? $"{server.Name} — {server.PingMs} ms, {server.OnlinePlayers}/{server.MaxPlayers} players"
                    : $"{server.Name} — Offline";
            }
            catch (OperationCanceledException) { StatusMessage = $"{server.Name} — Timed out."; }
            catch (Exception ex) { StatusMessage = $"Ping error: {ex.Message}"; }
        }

        [RelayCommand]
        private async Task ToggleFavorite(ServerInfo? server)
        {
            if (server == null) return;
            server.IsFavorite = !server.IsFavorite;
            await _serverService.UpdateServerAsync(server);
            // Re-sort (favorites first)
            var sorted = Servers.OrderByDescending(s => s.IsFavorite).ThenBy(s => s.Name).ToList();
            Servers = new ObservableCollection<ServerInfo>(sorted);
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

            await _serverService.DeleteServerAsync(server.Id);
            Servers.Remove(server);
            if (SelectedServer?.Id == server.Id) SelectedServer = null;
            StatusMessage = $"'{server.Name}' removed.";
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
            catch { }
        }
    }
}
