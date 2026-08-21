using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
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
using VayuClient.Services.Instance;
using VayuClient.Services.Java;
using VayuClient.Services.Launch;
using VayuClient.Services.Server;

namespace VayuClient.ViewModels
{
    public partial class HomeViewModel : ObservableObject, ILifecycleViewModel
    {
        private readonly MainViewModel _main;
        private readonly ILaunchService _launchService;
        private readonly IAccountService _accountService;
        private readonly IInstanceService _instanceService;
        private readonly IJavaRuntimeService _javaService;
        private readonly IServerService _serverService;
        private readonly BackendApiClient? _backendApi;
        private readonly SignalRClientService? _signalR;
        private CancellationTokenSource? _launchCts;
        private CancellationTokenSource? _partnerPingCts;
        private bool _disposed;
        private bool _isActivePage = true;

        // ─── Selected Instance Properties (Single Source of Truth) ────────────

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(ActiveInstanceName))]
        [NotifyPropertyChangedFor(nameof(ActiveInstanceVersion))]
        [NotifyPropertyChangedFor(nameof(ActiveInstanceLoaderUpper))]
        [NotifyPropertyChangedFor(nameof(ActiveInstanceRamDisplay))]
        [NotifyPropertyChangedFor(nameof(ActiveInstanceModCountDisplay))]
        [NotifyPropertyChangedFor(nameof(ActiveInstanceHeroSubtitle))]
        [NotifyPropertyChangedFor(nameof(ActiveInstanceBadgeDetails))]
        [NotifyPropertyChangedFor(nameof(HasActiveInstance))]
        private MinecraftInstance? _activeInstance;

        public string ActiveInstanceName => ActiveInstance != null 
            ? ActiveInstance.Name 
            : (Instances.Count > 0 ? "Select an Installation" : "Create New Installation");

        public string ActiveInstanceVersion => ActiveInstance != null 
            ? ActiveInstance.MinecraftVersion 
            : "No Version";

        public string ActiveInstanceLoaderUpper => ActiveInstance != null 
            ? ActiveInstance.Loader.ToUpperInvariant() 
            : "VANILLA";

        public string ActiveInstanceRamDisplay => ActiveInstance != null 
            ? ActiveInstance.DisplayRam 
            : "4096 MB";

        public string ActiveInstanceModCountDisplay
        {
            get
            {
                if (ActiveInstance == null) return "0 Mods";
                try
                {
                    var modsDir = Path.Combine(ActiveInstance.GameDirectory, "mods");
                    if (Directory.Exists(modsDir))
                    {
                        var jars = Directory.GetFiles(modsDir, "*.jar", SearchOption.TopDirectoryOnly);
                        return $"{jars.Length} Mods";
                    }
                }
                catch { }
                return "0 Mods";
            }
        }

        public string ActiveInstanceHeroSubtitle => ActiveInstance != null
            ? $"{ActiveInstance.MinecraftVersion} • {ActiveInstance.Loader} • {ActiveInstance.DisplayRam} • {ActiveInstanceModCountDisplay}"
            : "Choose an installation from the dock below or click + to create one.";

        public string ActiveInstanceBadgeDetails => ActiveInstance != null
            ? $"{ActiveInstance.MinecraftVersion} ({ActiveInstance.Loader}) • {ActiveInstance.DisplayRam}"
            : string.Empty;

        public bool HasActiveInstance => ActiveInstance != null;

        public ObservableCollection<MinecraftInstance> Instances => _main.Instances;

        // ─── Profile & Header Properties ──────────────────────────────────────

        [ObservableProperty]
        private UserProfile? _activeProfile;

        public string ProfileUsernameDisplay => ActiveProfile != null 
            ? (!string.IsNullOrEmpty(ActiveProfile.Username) ? ActiveProfile.Username : "Player") 
            : "Not signed in";

        public string ProfileSubtitleDisplay => ActiveProfile != null 
            ? (ActiveProfile.AccountType == AccountType.Microsoft ? "Microsoft Account" : "Offline Profile") 
            : "Create or select a profile";

        // ─── Play Button & Launch Progress States ──────────────────────────────

        [ObservableProperty]
        private bool _isBusy;

        [ObservableProperty]
        private bool _isPlaying;

        [ObservableProperty]
        private string _playButtonText = "PLAY";

        [ObservableProperty]
        private string _playButtonSubText = "READY TO LAUNCH";

        [ObservableProperty]
        private bool _isProgressVisible;

        [ObservableProperty]
        private double _progressPercentage;

        [ObservableProperty]
        private string _progressStatus = string.Empty;

        [ObservableProperty]
        private string _progressDetail = string.Empty;

        [ObservableProperty]
        private string _progressSpeed = string.Empty;

        [ObservableProperty]
        private string _launcherLogs = string.Empty;

        [ObservableProperty]
        private bool _isConsoleExpanded;

        // ─── Backend Connectivity Indicator ───────────────────────────────────

        [ObservableProperty]
        private string _backendStatus = "Connecting...";

        [ObservableProperty]
        private bool _backendConnected;

        [ObservableProperty]
        private string _latestReleaseTag = string.Empty;

        // ─── Dynamic Hero Background Wallpaper System ──────────────────────────

        private static readonly string[] _availableWallpapers = new[]
        {
            "/Assets/Images/bg_pvp_arena.jpg",
            "/Assets/Images/vayu_minecraft_hero.jpg",
            "/Assets/Images/bg_mountain_aurora.jpg",
            "/Assets/Images/bg_cherry_grove.jpg",
            "/Assets/Images/bg_cyber_nether.jpg",
            "/Assets/Images/bg_lush_caves.jpg",
            "/Assets/Images/bg_ocean_monument.jpg",
            "/Assets/Images/bg_fantasy_islands.jpg"
        };
        private int _currentWallpaperIndex = 0;
        private bool _userManuallyOverrodeWallpaper = false;

        [ObservableProperty]
        private string _heroBackgroundPath = "/Assets/Images/bg_pvp_arena.jpg";

        // ─── Lunar-Style Content Dashboard Collections ─────────────────────────

        public ObservableCollection<HomeCardItem> FeaturedCards { get; } = new();
        public ObservableCollection<HomeCardItem> NewsCards { get; } = new();
        public ObservableCollection<HomeCardItem> UpdateCards { get; } = new();
        public ObservableCollection<HomeCardItem> ModHighlightCards { get; } = new();
        public ObservableCollection<PartneredServerCard> PartneredServers { get; } = new();

        // ─── Constructor ───────────────────────────────────────────────────────

        public HomeViewModel(MainViewModel main)
        {
            _main = main;
            _launchService = ServiceLocator.Resolve<ILaunchService>();
            _accountService = ServiceLocator.Resolve<IAccountService>();
            _instanceService = ServiceLocator.Resolve<IInstanceService>();
            _javaService = ServiceLocator.Resolve<IJavaRuntimeService>();
            _serverService = ServiceLocator.Resolve<IServerService>();

            try { _backendApi = ServiceLocator.Resolve<BackendApiClient>(); } catch { }
            try { _signalR = ServiceLocator.Resolve<SignalRClientService>(); } catch { }

            _launchService.StateChanged += OnLaunchStateChanged;
            _launchService.DownloadProgressChanged += OnDownloadProgressChanged;
            _accountService.ActiveProfileChanged += p => Dispatch(RefreshProfile);
            _instanceService.InstancesChanged += () => Dispatch(RefreshProfile);

            if (_signalR != null)
            {
                _signalR.ConnectionStateChanged += connected =>
                {
                    BackendConnected = connected;
                    BackendStatus = connected ? "Connected" : "Reconnecting...";
                };

                _signalR.NewReleasePublished += tag =>
                {
                    LatestReleaseTag = tag;
                    _main.ShowNotification("New Update Available", $"VayuClient {tag} is available!", NotificationType.Info);
                };

                _signalR.RemoteBroadcastReceived += (msg, level, dur) =>
                {
                    var type = level.Equals("Critical", StringComparison.OrdinalIgnoreCase) ? NotificationType.Error :
                               level.Equals("Warning", StringComparison.OrdinalIgnoreCase) ? NotificationType.Warning :
                               level.Equals("Success", StringComparison.OrdinalIgnoreCase) ? NotificationType.Success : NotificationType.Info;
                    _main.ShowNotification("📢 Broadcast Alert", msg, type);
                };

                _signalR.DirectNotificationReceived += (title, msg, level) =>
                {
                    var type = level.Equals("Critical", StringComparison.OrdinalIgnoreCase) ? NotificationType.Error :
                               level.Equals("Warning", StringComparison.OrdinalIgnoreCase) ? NotificationType.Warning : NotificationType.Info;
                    _main.ShowNotification(title, msg, type);
                };

                _signalR.AnnouncementBroadcast += (title, msg, level) =>
                {
                    _ = Task.Run(FetchBackendDataAsync);
                };

                _signalR.AnnouncementDeleted += id =>
                {
                    _ = Task.Run(FetchBackendDataAsync);
                };

                _signalR.NewsUpdated += () =>
                {
                    _ = Task.Run(FetchBackendDataAsync);
                };
            }

            CrashLogger.LogsUpdated += () =>
            {
                if (_isActivePage && IsConsoleExpanded)
                {
                    Dispatch(() => LauncherLogs = CrashLogger.GetLiveLogsText());
                }
            };

            PopulateContentCards();
            PopulatePartneredServers();
            RefreshProfile();
        }

        // ─── Lifecycle ────────────────────────────────────────────────────────

        public Task InitializeAsync()
        {
            RefreshProfile();
            _ = Task.Run(PingPartneredServersAsync);
            return Task.CompletedTask;
        }

        public void Activate()
        {
            _isActivePage = true;
            RefreshProfile();
            if (IsConsoleExpanded)
            {
                LauncherLogs = CrashLogger.GetLiveLogsText();
            }

            _ = Task.Run(FetchBackendDataAsync);
            _ = Task.Run(PingPartneredServersAsync);
        }

        public void Deactivate()
        {
            _isActivePage = false;
            _partnerPingCts?.Cancel();
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
            _isActivePage = false;
            _partnerPingCts?.Cancel();
            _partnerPingCts?.Dispose();
        }

        private static void Dispatch(Action action)
        {
            var app = Application.Current;
            if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess() && !app.Dispatcher.HasShutdownStarted)
            {
                try { app.Dispatcher.BeginInvoke(action); } catch { action(); }
            }
            else
            {
                action();
            }
        }

        // ─── Profile & Instance Sync ──────────────────────────────────────────

        public void RefreshProfile()
        {
            ActiveProfile = _accountService.ActiveProfile;
            var instances = _instanceService.GetAllInstances();
            var active = instances.FirstOrDefault(i => i.IsActive) ?? instances.FirstOrDefault();
            
            ActiveInstance = active;
            OnPropertyChanged(nameof(ProfileUsernameDisplay));
            OnPropertyChanged(nameof(ProfileSubtitleDisplay));
            OnPropertyChanged(nameof(ActiveInstanceName));
            OnPropertyChanged(nameof(ActiveInstanceVersion));
            OnPropertyChanged(nameof(ActiveInstanceLoaderUpper));
            OnPropertyChanged(nameof(ActiveInstanceRamDisplay));
            OnPropertyChanged(nameof(ActiveInstanceModCountDisplay));
            OnPropertyChanged(nameof(ActiveInstanceHeroSubtitle));
            OnPropertyChanged(nameof(ActiveInstanceBadgeDetails));
            OnPropertyChanged(nameof(HasActiveInstance));

            if (ActiveInstance != null && !_userManuallyOverrodeWallpaper)
            {
                HeroBackgroundPath = ResolveArtworkForInstance(ActiveInstance);
            }
        }

        private static string ResolveArtworkForInstance(MinecraftInstance instance)
        {
            string ver = instance.MinecraftVersion ?? string.Empty;
            string name = (instance.Name ?? string.Empty).ToLowerInvariant();

            if (ver.StartsWith("26.2") || name.Contains("pvp") || name.Contains("combat"))
                return "/Assets/Images/bg_pvp_arena.jpg";
            if (ver.StartsWith("26.1") || ver.Contains("26.1.2"))
                return "/Assets/Images/bg_mountain_aurora.jpg";
            if (ver.StartsWith("1.21"))
                return "/Assets/Images/bg_cherry_grove.jpg";
            if (name.Contains("survival") || name.Contains("smp"))
                return "/Assets/Images/bg_lush_caves.jpg";
            if (name.Contains("ocean") || name.Contains("monument"))
                return "/Assets/Images/bg_ocean_monument.jpg";

            return "/Assets/Images/vayu_minecraft_hero.jpg";
        }

        [RelayCommand]
        public void SelectInstance(MinecraftInstance? instance)
        {
            if (instance == null) return;
            _instanceService.SetActiveInstance(instance.InstanceId);
            RefreshProfile();
            _main.ShowNotification("Instance Selected", $"Active installation set to {instance.Name}", NotificationType.Info);
        }

        [RelayCommand]
        public void CycleWallpaper()
        {
            _userManuallyOverrodeWallpaper = true;
            _currentWallpaperIndex = (_currentWallpaperIndex + 1) % _availableWallpapers.Length;
            HeroBackgroundPath = _availableWallpapers[_currentWallpaperIndex];
            _main.ShowNotification("Theme Wallpaper", $"Wallpaper theme updated ({_currentWallpaperIndex + 1}/{_availableWallpapers.Length})", NotificationType.Info);
        }

        // ─── Play / Launch Logic ──────────────────────────────────────────────

        [RelayCommand]
        public async Task PlayAsync()
        {
            if (IsBusy) return;

            if (ActiveInstance == null)
            {
                _main.NavigateToCommand.Execute("InstallationManager");
                _main.ShowNotification("No Instance Selected", "Please create or select an installation to play.", NotificationType.Warning);
                return;
            }

            if (ActiveProfile == null)
            {
                _main.NavigateToCommand.Execute("Accounts");
                _main.ShowNotification("No Account Selected", "Please select or link an account before launching.", NotificationType.Warning);
                return;
            }

            IsBusy = true;
            IsProgressVisible = true;
            ProgressPercentage = 0;
            ProgressStatus = "Preparing game engine...";
            ProgressDetail = $"Initializing {ActiveInstance.Name}...";
            PlayButtonText = "LAUNCHING...";
            PlayButtonSubText = "STARTING ENGINE";

            _launchCts = new CancellationTokenSource();

            try
            {
                bool success = await _launchService.LaunchInstanceAsync(ActiveInstance.InstanceId, _launchCts.Token);
                if (!success)
                {
                    _main.ShowNotification("Launch Failed", "Could not start Minecraft. Check console logs for details.", NotificationType.Error);
                }
            }
            catch (OperationCanceledException)
            {
                _main.ShowNotification("Launch Cancelled", "Launch operation was cancelled by user.", NotificationType.Info);
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("HomeViewModel.PlayAsync", ex);
                _main.ShowNotification("Launch Error", ex.Message, NotificationType.Error);
            }
            finally
            {
                IsBusy = false;
                IsProgressVisible = false;
                _launchCts?.Dispose();
                _launchCts = null;
            }
        }

        [RelayCommand]
        public void CancelLaunch()
        {
            _launchCts?.Cancel();
            IsBusy = false;
            IsProgressVisible = false;
            PlayButtonText = "PLAY";
            PlayButtonSubText = "READY TO LAUNCH";
        }

        [RelayCommand]
        public void StopGame()
        {
            try
            {
                _launchService.KillActiveGame();
                IsPlaying = false;
                _main.ShowNotification("Game Stopped", "Minecraft process was terminated.", NotificationType.Info);
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("HomeViewModel.StopGame", ex);
            }
        }

        [RelayCommand]
        public void ToggleConsole()
        {
            IsConsoleExpanded = !IsConsoleExpanded;
            if (IsConsoleExpanded)
            {
                LauncherLogs = CrashLogger.GetLiveLogsText();
            }
        }

        [RelayCommand]
        public void CreateNewInstance()
        {
            _main.NavigateToCommand.Execute("InstallationManager");
        }

        [RelayCommand]
        public void GoToVersions()
        {
            _main.NavigateToCommand.Execute("Versions");
        }

        [RelayCommand]
        public void GoToAccounts()
        {
            _main.NavigateToCommand.Execute("Accounts");
        }

        [RelayCommand]
        public void GoToSettings()
        {
            _main.NavigateToCommand.Execute("Settings");
        }

        [RelayCommand]
        public void GoToMods()
        {
            _main.NavigateToCommand.Execute("Mods");
        }

        // ─── Launch Service Event Handlers ────────────────────────────────────

        private void OnLaunchStateChanged(LaunchState state, string message)
        {
            Dispatch(() =>
            {
                ProgressStatus = message;
                switch (state)
                {
                    case LaunchState.Preparing:
                        ProgressDetail = message;
                        ProgressSpeed = string.Empty;
                        PlayButtonText = "PREPARING...";
                        PlayButtonSubText = "CHECKING FILES";
                        break;
                    case LaunchState.Downloading:
                        PlayButtonText = "DOWNLOADING...";
                        PlayButtonSubText = "FETCHING ASSETS";
                        break;
                    case LaunchState.Installing:
                        ProgressDetail = message;
                        ProgressSpeed = string.Empty;
                        PlayButtonText = "INSTALLING...";
                        PlayButtonSubText = "CONFIGURING JVM";
                        break;
                    case LaunchState.Launching:
                        ProgressDetail = "Spawning Minecraft Java process...";
                        ProgressPercentage = 100;
                        ProgressSpeed = string.Empty;
                        PlayButtonText = "IGNITION...";
                        PlayButtonSubText = "LAUNCHING PROCESS";
                        break;
                    case LaunchState.Playing:
                        IsPlaying = true;
                        IsBusy = false;
                        IsProgressVisible = false;
                        PlayButtonText = "IN-GAME";
                        PlayButtonSubText = "RUNNING";
                        break;
                    case LaunchState.GameClosed:
                    case LaunchState.Failed:
                    case LaunchState.Idle:
                        IsPlaying = false;
                        IsBusy = false;
                        IsProgressVisible = false;
                        ProgressSpeed = string.Empty;
                        PlayButtonText = "PLAY";
                        PlayButtonSubText = "READY TO LAUNCH";
                        break;
                }
            });
        }

        private void OnDownloadProgressChanged(DownloadProgressInfo info)
        {
            Dispatch(() =>
            {
                ProgressPercentage = info.Percentage;
                ProgressDetail = info.CurrentOperation;
                ProgressSpeed = info.SpeedDisplay;
            });
        }

        public bool HasFeaturedCards => FeaturedCards.Count > 0;
        public bool HasNewsCards => NewsCards.Count > 0;
        public bool HasModHighlightCards => ModHighlightCards.Count > 0;
        public bool HasPartneredServers => PartneredServers.Count > 0;

        // ─── Real Content Sourced from Backend ─────────────────────────────────

        private void PopulateContentCards()
        {
            FeaturedCards.Clear();
            NewsCards.Clear();
            ModHighlightCards.Clear();

            NewsCards.Add(new HomeCardItem
            {
                Category = "UPDATE",
                DateTag = "v1.8.3 Active",
                Title = "VayuClient Engine & HUD Rebuild",
                Description = "All-new overhead player hearts, combat damage indicator telemetry, and ultra-high FPS pipeline.",
                ImagePath = "/Assets/Images/bg_mountain_aurora.jpg",
                ActionText = "Open Mods Library",
                NavigationTarget = "Mods"
            });

            NewsCards.Add(new HomeCardItem
            {
                Category = "PERFORMANCE",
                DateTag = "FPS Engine",
                Title = "1000+ FPS Performance Pipeline",
                Description = "Fine-tuned memory allocation, zero-latency HUD rendering, and optimal chunk loading speeds.",
                ImagePath = "/Assets/Images/bg_cherry_grove.jpg",
                ActionText = "Configure Settings",
                NavigationTarget = "Settings"
            });

            NewsCards.Add(new HomeCardItem
            {
                Category = "COMMUNITY",
                DateTag = "Discord Community",
                Title = "Join Official VayuClient Discord",
                Description = "Chat with developers, report bugs, get live notifications, and join exclusive community events.",
                ImagePath = "/Assets/Images/bg_cyber_nether.jpg",
                ActionText = "Join Server",
                ActionUrl = "https://discord.gg/aXUkFajMc"
            });

            OnPropertyChanged(nameof(HasFeaturedCards));
            OnPropertyChanged(nameof(HasNewsCards));
            OnPropertyChanged(nameof(HasModHighlightCards));
        }

        private void PopulatePartneredServers()
        {
            PartneredServers.Clear();
            OnPropertyChanged(nameof(HasPartneredServers));
        }

        // ─── Real Server List Ping (SLP) for Partnered Servers ─────────────────

        private async Task PingPartneredServersAsync()
        {
            if (PartneredServers.Count == 0) return;
            _partnerPingCts?.Cancel();
            _partnerPingCts = new CancellationTokenSource();
            var token = _partnerPingCts.Token;

            foreach (var srv in PartneredServers)
            {
                if (token.IsCancellationRequested) break;

                _ = Task.Run(async () =>
                {
                    try
                    {
                        var srvInfo = new ServerInfo
                        {
                            Name = srv.Name,
                            Address = srv.Address,
                            Port = 25565
                        };

                        bool ok = await _serverService.PingServerAsync(srvInfo, token).ConfigureAwait(false);
                        Dispatch(() =>
                        {
                            if (ok && srvInfo.IsOnline)
                            {
                                srv.IsOnline = true;
                                srv.OnlinePlayers = srvInfo.OnlinePlayers;
                                srv.MaxPlayers = srvInfo.MaxPlayers;
                                srv.LatencyMs = (int)srvInfo.PingMs;
                                srv.PlayerCountDisplay = $"{srvInfo.OnlinePlayers:N0} / {srvInfo.MaxPlayers:N0} Players";
                                srv.StatusDisplay = $"Online • {srvInfo.PingMs}ms";
                            }
                            else
                            {
                                srv.IsOnline = false;
                                srv.PlayerCountDisplay = "Player count unavailable";
                                srv.StatusDisplay = "Offline / Unreachable";
                            }
                        });
                    }
                    catch
                    {
                        Dispatch(() =>
                        {
                            srv.IsOnline = false;
                            srv.PlayerCountDisplay = "Player count unavailable";
                            srv.StatusDisplay = "Offline";
                        });
                    }
                }, token);
            }

            await Task.CompletedTask;
        }

        [RelayCommand]
        public void ConnectPartnerServer(PartneredServerCard? server)
        {
            if (server == null) return;
            _main.ShowNotification("Connecting to Server", $"Launching {ActiveInstance?.Name ?? "Minecraft"} and connecting to {server.Address}...", NotificationType.Info);
            _ = PlayAsync();
        }

        [RelayCommand]
        public void OpenCardAction(HomeCardItem? card)
        {
            if (card == null) return;
            if (!string.IsNullOrEmpty(card.NavigationTarget))
            {
                _main.NavigateToCommand.Execute(card.NavigationTarget);
            }
            else if (!string.IsNullOrEmpty(card.ActionUrl))
            {
                try { Process.Start(new ProcessStartInfo(card.ActionUrl) { UseShellExecute = true }); } catch { }
            }
        }

        [RelayCommand]
        public void OpenDiscord()
        {
            try
            {
                Process.Start(new ProcessStartInfo("https://discord.gg/aXUkFajMc") { UseShellExecute = true });
            }
            catch { }
        }

        // ─── Backend Data Synchronization ─────────────────────────────────────

        private async Task FetchBackendDataAsync()
        {
            if (_backendApi == null) return;
            try
            {
                using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(6));
                bool alive = await _backendApi.IsAliveAsync(cts.Token).ConfigureAwait(false);
                Dispatch(() =>
                {
                    BackendConnected = alive;
                    BackendStatus = alive ? "Connected" : "Offline";
                });

                if (alive)
                {
                    // 1. Fetch real partner servers from backend
                    var servers = await _backendApi.GetServersAsync(cts.Token).ConfigureAwait(false);
                    if (servers != null)
                    {
                        var featured = servers.Where(s => s.IsFeatured).ToList();
                        Dispatch(() =>
                        {
                            PartneredServers.Clear();
                            foreach (var s in featured)
                            {
                                PartneredServers.Add(new PartneredServerCard
                                {
                                    Name = s.Name,
                                    Address = s.Address,
                                    Description = s.Motd ?? "Official Partner Server",
                                    VersionRange = s.Version ?? "1.8.x – 1.21.x",
                                    IconPath = "/Assets/Images/vayu_logo.png",
                                    PlayerCountDisplay = s.IsOnline ? $"{s.OnlinePlayers:N0} / {s.MaxPlayers:N0} Players" : "Pinging...",
                                    StatusDisplay = s.IsOnline ? $"Online • {s.PingMs}ms" : "Checking..."
                                });
                            }
                            OnPropertyChanged(nameof(HasPartneredServers));
                        });

                        if (featured.Count > 0)
                        {
                            _ = PingPartneredServersAsync();
                        }
                    }

                    // 2. Fetch real announcements from backend
                    var announcements = await _backendApi.GetAnnouncementsAsync(cts.Token).ConfigureAwait(false);
                    if (announcements != null)
                    {
                        var active = announcements.Where(a => a.IsActive).ToList();
                        Dispatch(() =>
                        {
                            NewsCards.Clear();
                            foreach (var a in active)
                            {
                                NewsCards.Add(new HomeCardItem
                                {
                                    Title = a.Title,
                                    Description = a.Content,
                                    Category = a.Level.ToUpperInvariant(),
                                    BadgeColor = a.Level.ToLowerInvariant() switch
                                    {
                                        "critical" => "#EF4444",
                                        "warning" => "#F59E0B",
                                        _ => "#38BDF8"
                                    },
                                    ImagePath = "/Assets/Images/bg_mountain_aurora.jpg",
                                    DateTag = a.StartsAt.ToString("MMM yyyy"),
                                    ActionText = !string.IsNullOrEmpty(a.ActionLabel) ? a.ActionLabel : "Learn More",
                                    ActionUrl = a.ActionUrl ?? string.Empty
                                });
                            }
                            OnPropertyChanged(nameof(HasNewsCards));
                        });
                    }
                }
            }
            catch { }
        }
    }
}
