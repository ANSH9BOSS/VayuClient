using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Account;
using VayuClient.Services.Instance;
using VayuClient.Services.Java;
using VayuClient.Services.Launch;

namespace VayuClient.ViewModels
{
    public partial class HomeViewModel : ObservableObject
    {
        private readonly MainViewModel _main;
        private readonly ILaunchService _launchService;
        private readonly IAccountService _accountService;
        private readonly IInstanceService _instanceService;
        private readonly IJavaRuntimeService _javaService;
        private CancellationTokenSource? _launchCts;

        [ObservableProperty]
        private string _activeInstanceName = "No Minecraft installations yet";

        [ObservableProperty]
        private string _activeInstanceSubtitle = "Create an instance to start playing";

        [ObservableProperty]
        private MinecraftInstance? _activeInstance;

        [ObservableProperty]
        private int _instancesCount = 0;

        [ObservableProperty]
        private UserProfile? _activeProfile;

        [ObservableProperty]
        private string _profileName = "No profile selected";

        [ObservableProperty]
        private string _profileType = "Select a profile to play";

        [ObservableProperty]
        private string _javaStatusText = "Java Auto-Detected";

        [ObservableProperty]
        private string _selectedVersion = "No version selected";

        [ObservableProperty]
        private string _instanceDetails = "Default Instance";

        [ObservableProperty]
        private bool _hasProfile;

        [ObservableProperty]
        private bool _hasActiveInstance;

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
        private string _gameSessionStatus = "Minecraft is active";

        [ObservableProperty]
        private string _launcherLogs = string.Empty;

        [ObservableProperty]
        private bool _isConsoleExpanded = false;

        public ObservableCollection<MinecraftInstance> Instances => _main.Instances;

        public string ProfileUsernameDisplay => ActiveProfile != null ? ActiveProfile.Username : "No Profile Selected";
        public string ProfileSubtitleDisplay => ActiveProfile != null ? JavaStatusText : "Create or select a profile";

        public string ActiveLoaderDisplay => ActiveInstance != null ? ActiveInstance.Loader.ToUpperInvariant() : "MINECRAFT";
        private static readonly string[] _availableWallpapers = new[]
        {
            "/Assets/Images/vayu_minecraft_hero.jpg",
            "/Assets/Images/bg_cyber_nether.jpg",
            "/Assets/Images/bg_cherry_grove.jpg",
            "/Assets/Images/bg_lush_caves.jpg",
            "/Assets/Images/bg_mountain_aurora.jpg",
            "/Assets/Images/bg_fantasy_islands.jpg",
            "/Assets/Images/bg_ocean_monument.jpg"
        };
        private int _currentWallpaperIndex = 0;

        [ObservableProperty]
        private string _heroBackgroundPath = "/Assets/Images/vayu_minecraft_hero.jpg";

        [RelayCommand]
        public void CycleWallpaper()
        {
            _currentWallpaperIndex = (_currentWallpaperIndex + 1) % _availableWallpapers.Length;
            HeroBackgroundPath = _availableWallpapers[_currentWallpaperIndex];
            _main.ShowNotification("Wallpaper Changed", $"Switched hero wallpaper theme ({_currentWallpaperIndex + 1}/{_availableWallpapers.Length})", NotificationType.Info);
        }

        public string ActiveVersionDisplay => ActiveInstance != null ? ActiveInstance.MinecraftVersion : "No Version";
        public string ActiveRamDisplay => ActiveInstance != null ? ActiveInstance.DisplayRam : "4096 MB";

        public HomeViewModel(MainViewModel main)
        {
            _main = main;
            _launchService = ServiceLocator.Resolve<ILaunchService>();
            _accountService = ServiceLocator.Resolve<IAccountService>();
            _instanceService = ServiceLocator.Resolve<IInstanceService>();
            _javaService = ServiceLocator.Resolve<IJavaRuntimeService>();

            _launchService.StateChanged += OnLaunchStateChanged;
            _launchService.DownloadProgressChanged += OnDownloadProgressChanged;
            _accountService.ActiveProfileChanged += (p) => Dispatch(RefreshProfile);
            _instanceService.InstancesChanged += () => Dispatch(RefreshProfile);

            LauncherLogs = CrashLogger.GetLiveLogsText();
            CrashLogger.LogsUpdated += () =>
            {
                Dispatch(() =>
                {
                    LauncherLogs = CrashLogger.GetLiveLogsText();
                });
            };

            RefreshProfile();
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

        private void OnLaunchStateChanged(LaunchState state, string message)
        {
            Dispatch(() =>
            {
                switch (state)
                {
                    case LaunchState.Idle:
                    case LaunchState.GameClosed:
                        IsBusy = false;
                        IsPlaying = false;
                        IsProgressVisible = false;
                        PlayButtonText = HasActiveInstance ? "PLAY" : "CREATE INSTANCE";
                        PlayButtonSubText = HasActiveInstance ? "READY TO LAUNCH" : "OPEN CREATION WIZARD";
                        break;
                    case LaunchState.Preparing:
                        IsBusy = true;
                        IsPlaying = false;
                        IsProgressVisible = true;
                        ProgressStatus = message;
                        PlayButtonText = "PREPARING...";
                        PlayButtonSubText = message;
                        break;
                    case LaunchState.Downloading:
                        IsBusy = true;
                        IsPlaying = false;
                        IsProgressVisible = true;
                        ProgressStatus = message;
                        PlayButtonText = "DOWNLOADING...";
                        PlayButtonSubText = message;
                        break;
                    case LaunchState.Installing:
                        IsBusy = true;
                        IsPlaying = false;
                        IsProgressVisible = true;
                        ProgressStatus = message;
                        PlayButtonText = "EXTRACTING...";
                        PlayButtonSubText = message;
                        break;
                    case LaunchState.Launching:
                        IsBusy = true;
                        IsPlaying = false;
                        IsProgressVisible = true;
                        ProgressStatus = message;
                        PlayButtonText = "LAUNCHING...";
                        PlayButtonSubText = message;
                        break;
                    case LaunchState.Playing:
                        IsBusy = false;
                        IsPlaying = true;
                        IsProgressVisible = false;
                        PlayButtonText = "IN-GAME";
                        PlayButtonSubText = "CLICK TO STOP GAME";
                        GameSessionStatus = $"{ActiveInstanceName} is active";
                        break;
                    case LaunchState.Failed:
                        IsBusy = false;
                        IsPlaying = false;
                        IsProgressVisible = false;
                        PlayButtonText = "PLAY";
                        PlayButtonSubText = "LAUNCH FAILED - RETRY";
                        _main.ShowNotification("Launch Failed", message, NotificationType.Error);
                        break;
                }
            });
        }

        private void OnDownloadProgressChanged(DownloadProgressInfo progress)
        {
            Dispatch(() =>
            {
                IsProgressVisible = true;
                ProgressPercentage = progress.Percentage;
                ProgressStatus = progress.CurrentOperation;
                ProgressDetail = progress.CurrentFileName;
                ProgressSpeed = progress.SpeedMBPerSec > 0 ? $"{progress.SpeedMBPerSec:F1} MB/s" : string.Empty;
            });
        }

        public void RefreshProfile()
        {
            try
            {
                ActiveProfile = _accountService.ActiveProfile;
                if (ActiveProfile != null)
                {
                    ProfileName = ActiveProfile.Username;
                    ProfileType = ActiveProfile.AccountType == AccountType.Microsoft
                        ? "Microsoft Account"
                        : "Offline Profile";
                    HasProfile = true;
                }
                else
                {
                    ProfileName = "No profile selected";
                    ProfileType = "Select a profile to play";
                    HasProfile = false;
                }

                var allInstances = _instanceService.GetAllInstances();
                InstancesCount = allInstances.Count;

                var activeInstance = _instanceService.GetActiveInstance();
                ActiveInstance = activeInstance;
                if (activeInstance != null)
                {
                    HasActiveInstance = true;
                    PlayButtonText = "PLAY";
                    ActiveInstanceName = activeInstance.Name;
                    ActiveInstanceSubtitle = $"{activeInstance.MinecraftVersion} ({activeInstance.Loader}) • {activeInstance.DisplayRam}";
                    SelectedVersion = $"{activeInstance.MinecraftVersion} ({activeInstance.Loader})";
                    InstanceDetails = $"{activeInstance.Name} • {activeInstance.DisplayRam}";

                    var reqJava = _javaService.GetRequiredJavaVersion(activeInstance.MinecraftVersion);
                    var java = _javaService.FindCompatibleRuntime(reqJava);
                    JavaStatusText = java != null ? $"Java {java.MajorVersion} Auto-Detected" : $"Java {reqJava} Recommended";
                }
                else
                {
                    HasActiveInstance = false;
                    PlayButtonText = "CREATE INSTANCE";
                    ActiveInstanceName = allInstances.Count > 0 ? "Select an instance" : "No Minecraft installations";
                    ActiveInstanceSubtitle = allInstances.Count > 0 ? "Choose an instance to play" : "Install a Minecraft version to get started";
                    SelectedVersion = "No version selected";
                    InstanceDetails = "No instance selected";
                    JavaStatusText = "Java Auto-Detected";
                }

                OnPropertyChanged(nameof(ProfileUsernameDisplay));
                OnPropertyChanged(nameof(ProfileSubtitleDisplay));
                OnPropertyChanged(nameof(ActiveLoaderDisplay));
                OnPropertyChanged(nameof(ActiveVersionDisplay));
                OnPropertyChanged(nameof(ActiveRamDisplay));

                if (!IsPlaying)
                {
                    try
                    {
                        var discordRpc = ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>();
                        if (activeInstance != null)
                        {
                            discordRpc?.SetInLauncherPresence(activeInstance.Name, activeInstance.MinecraftVersion, activeInstance.Loader);
                        }
                        else
                        {
                            discordRpc?.SetInLauncherPresence();
                        }
                    }
                    catch { }
                }
            }
            catch
            {
                HasProfile = false;
                HasActiveInstance = false;
            }
        }

        [RelayCommand]
        public void SelectInstance(MinecraftInstance? instance)
        {
            if (instance == null) return;
            _instanceService.SetActiveInstance(instance.InstanceId);
            RefreshProfile();
            _main.ShowNotification("Instance Selected", $"Active instance switched to '{instance.Name}'.", NotificationType.Success);
        }

        [RelayCommand]
        public void ToggleConsole()
        {
            IsConsoleExpanded = !IsConsoleExpanded;
        }

        [RelayCommand]
        public async Task PlayAsync()
        {
            if (IsPlaying)
            {
                StopGame();
                return;
            }

            if (IsBusy)
            {
                return;
            }

            if (!HasActiveInstance || _instanceService.GetActiveInstance() == null)
            {
                _main.NavigateToCommand.Execute("Versions");
                return;
            }

            if (!HasProfile || _accountService.ActiveProfile == null)
            {
                _main.ShowNotification("No Profile", "Please create or select an offline profile or Microsoft account first.", NotificationType.Warning);
                _main.NavigateToCommand.Execute("Accounts");
                return;
            }

            var activeInstance = _instanceService.GetActiveInstance()!;

            _launchCts?.Cancel();
            _launchCts = new CancellationTokenSource();

            try
            {
                await _launchService.LaunchInstanceAsync(activeInstance.InstanceId, _launchCts.Token);
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Launch Error", ex.Message, NotificationType.Error);
            }
        }

        [RelayCommand]
        public void CancelLaunch()
        {
            _launchCts?.Cancel();
            IsBusy = false;
            IsProgressVisible = false;
            PlayButtonText = HasActiveInstance ? "PLAY" : "CREATE INSTANCE";
            PlayButtonSubText = HasActiveInstance ? "READY TO LAUNCH" : "OPEN CREATION WIZARD";
            _main.ShowNotification("Launch Cancelled", "Minecraft launch operation was cancelled.", NotificationType.Info);
        }

        [RelayCommand]
        private void StopGame()
        {
            _launchService.KillActiveGame();
            _launchCts?.Cancel();
            IsBusy = false;
            IsPlaying = false;
            PlayButtonText = HasActiveInstance ? "PLAY" : "CREATE INSTANCE";
            PlayButtonSubText = HasActiveInstance ? "READY TO LAUNCH" : "OPEN CREATION WIZARD";
            _main.ShowNotification("Game Terminated", "Active Minecraft instance was stopped.", NotificationType.Info);
        }

        [RelayCommand]
        private void ClearLogs()
        {
            CrashLogger.Clear();
            LauncherLogs = string.Empty;
        }

        [RelayCommand]
        private void CopyLogs()
        {
            try
            {
                if (!string.IsNullOrEmpty(LauncherLogs))
                {
                    Clipboard.SetText(LauncherLogs);
                    _main.ShowNotification("Logs Copied", "Launcher logs copied to clipboard.", NotificationType.Success);
                }
            }
            catch { }
        }

        [RelayCommand]
        private void CreateNewInstance()
        {
            _main.NavigateToCommand.Execute("Versions");
        }

        [RelayCommand]
        private void GoToAccounts()
        {
            _main.NavigateToCommand.Execute("Accounts");
        }

        [RelayCommand]
        private void GoToVersions()
        {
            _main.NavigateToCommand.Execute("Versions");
        }

        [RelayCommand]
        private void GoToInstallationManager()
        {
            _main.NavigateToCommand.Execute("InstallationManager");
        }

        [RelayCommand]
        private void GoToMods()
        {
            _main.NavigateToCommand.Execute("Mods");
        }

        [RelayCommand]
        private void GoToSettings()
        {
            _main.NavigateToCommand.Execute("Settings");
        }

        [RelayCommand]
        private void OpenGameFolder()
        {
            try
            {
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var dir = Path.Combine(appData, "VayuClient");
                Directory.CreateDirectory(dir);
                Process.Start(new ProcessStartInfo
                {
                    FileName = dir,
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Folder Error", ex.Message, NotificationType.Error);
            }
        }
    }
}
