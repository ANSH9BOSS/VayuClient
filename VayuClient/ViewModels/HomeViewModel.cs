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
                        PlayButtonText = "PREPARING...";
                        PlayButtonSubText = "CHECKING FILES";
                        break;
                    case LaunchState.Downloading:
                        PlayButtonText = "DOWNLOADING...";
                        PlayButtonSubText = "FETCHING ASSETS";
                        break;
                    case LaunchState.Installing:
                        PlayButtonText = "INSTALLING...";
                        PlayButtonSubText = "CONFIGURING JVM";
                        break;
                    case LaunchState.Launching:
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

        // ─── Lunar-Style Content Population ────────────────────────────────────

        private void PopulateContentCards()
        {
            FeaturedCards.Clear();
            FeaturedCards.Add(new HomeCardItem
            {
                Title = "VayuClient v1.6.0 Released — Next-Gen In-Game UI Overhaul",
                Description = "Experience a completely redesigned high-FPS title screen, custom glass pause overlay, floating cyber dust particles, and dynamic Sodium shader pipeline.",
                Category = "FEATURED RELEASE",
                BadgeColor = "#00D2FF",
                ImagePath = "/Assets/Images/vayu_minecraft_hero.jpg",
                DateTag = "v1.6.0 Official",
                ActionText = "Explore Mods",
                NavigationTarget = "Mods"
            });

            NewsCards.Clear();
            NewsCards.Add(new HomeCardItem
            {
                Title = "Minecraft 26.2 Architecture Support",
                Description = "Full capability engine support for Minecraft 26.2 with dynamic mappings, Java 25 compiler optimizations, and sub-millisecond input latency.",
                Category = "MINECRAFT UPDATE",
                BadgeColor = "#38BDF8",
                ImagePath = "/Assets/Images/bg_mountain_aurora.jpg",
                DateTag = "August 2026",
                ActionText = "View Instances",
                NavigationTarget = "InstallationManager"
            });
            NewsCards.Add(new HomeCardItem
            {
                Title = "Sodium & Iris Shader Pipeline",
                Description = "Embedded multi-threaded shader caching delivering buttery smooth 240+ FPS with full RTX ray-tracing and volumetric lighting.",
                Category = "GRAPHICS & FPS",
                BadgeColor = "#10B981",
                ImagePath = "/Assets/Images/bg_cherry_grove.jpg",
                DateTag = "Performance",
                ActionText = "Manage Mods",
                NavigationTarget = "Mods"
            });
            NewsCards.Add(new HomeCardItem
            {
                Title = "LungeHelper v2.0 PvP Suite",
                Description = "Advanced hit registration diagnostics, trajectory lines, cps counter, keystrokes, and clean crosshairs tailored for competitive Minecraft PvP.",
                Category = "PVP ENHANCEMENTS",
                BadgeColor = "#A78BFA",
                ImagePath = "/Assets/Images/bg_pvp_arena.jpg",
                DateTag = "PvP Ready",
                ActionText = "Configure Addons",
                NavigationTarget = "Mods"
            });

            ModHighlightCards.Clear();
            ModHighlightCards.Add(new HomeCardItem
            {
                Title = "ImmediatelyFast HUD Optimization",
                Description = "Optimizes immediate-mode GUI rendering for ultra-low frame times during intense PvP fights.",
                Category = "OPTIMIZATION",
                BadgeColor = "#34D399",
                ImagePath = "/Assets/Images/bg_cyber_nether.jpg",
                DateTag = "Pre-Installed",
                ActionText = "Open Mods",
                NavigationTarget = "Mods"
            });
            ModHighlightCards.Add(new HomeCardItem
            {
                Title = "Vayu In-Game Cyber Presence",
                Description = "Connect with friends and display real-time live Discord Rich Presence with rich animated in-game status.",
                Category = "COMMUNITY",
                BadgeColor = "#F59E0B",
                ImagePath = "/Assets/Images/bg_fantasy_islands.jpg",
                DateTag = "Discord RPC",
                ActionText = "Settings",
                NavigationTarget = "Settings"
            });
        }

        private void PopulatePartneredServers()
        {
            PartneredServers.Clear();
            PartneredServers.Add(new PartneredServerCard
            {
                Name = "Vayu PvP Network",
                Address = "pvp.vayuclient.net",
                Description = "Official VayuClient Partner PvP Arena featuring Practice 1v1, BedWars, and zero hit-delay.",
                VersionRange = "1.8.9 – 26.2",
                IconPath = "/Assets/Images/vayu_logo.png",
                PlayerCountDisplay = "Pinging...",
                StatusDisplay = "Checking..."
            });
            PartneredServers.Add(new PartneredServerCard
            {
                Name = "Hypixel Network",
                Address = "mc.hypixel.net",
                Description = "The world's largest Minecraft minigame server with SkyWars, BedWars, and SkyBlock.",
                VersionRange = "1.8.x – 26.2",
                IconPath = "/Assets/Images/bg_pvp_arena.jpg",
                PlayerCountDisplay = "Pinging...",
                StatusDisplay = "Checking..."
            });
            PartneredServers.Add(new PartneredServerCard
            {
                Name = "PvP Land",
                Address = "pvp.land",
                Description = "Competitive Practice PvP network with bot duels, ranked matchmaking, and Elo leaderboards.",
                VersionRange = "1.8.9 – 26.2",
                IconPath = "/Assets/Images/bg_cyber_nether.jpg",
                PlayerCountDisplay = "Pinging...",
                StatusDisplay = "Checking..."
            });
            PartneredServers.Add(new PartneredServerCard
            {
                Name = "Vayu Survival SMP",
                Address = "smp.vayuclient.net",
                Description = "Official community Survival Multiplayer SMP with player economy, custom land claiming, and dungeons.",
                VersionRange = "1.20.x – 26.2",
                IconPath = "/Assets/Images/bg_lush_caves.jpg",
                PlayerCountDisplay = "Pinging...",
                StatusDisplay = "Checking..."
            });
        }

        // ─── Real Server List Ping (SLP) for Partnered Servers ─────────────────

        private async Task PingPartneredServersAsync()
        {
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
                Process.Start(new ProcessStartInfo("https://discord.gg/vayuclient") { UseShellExecute = true });
            }
            catch { }
        }

        // ─── Backend Health Liveness ──────────────────────────────────────────

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
            }
            catch { }
        }
    }
}
