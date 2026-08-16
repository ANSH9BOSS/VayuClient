using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services;
using VayuClient.Services.Account;
using VayuClient.Services.Download;
using VayuClient.Services.Instance;
using VayuClient.Services.Launch;
using VayuClient.Services.Updates;

namespace VayuClient.ViewModels
{
    /// <summary>
    /// Root ViewModel: coordinates the sidebar navigation, permanent All Instances list,
    /// play engine, settings, installation manager, crash report, and notifications.
    /// </summary>
    public partial class MainViewModel : ObservableObject
    {
        [ObservableProperty]
        private string _currentPage = "Home"; // "Home", "InstallationManager", "Settings", "Versions", "Accounts"

        [ObservableProperty]
        private object? _currentPageViewModel;

        [ObservableProperty]
        private bool _isSplashVisible = true;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(ActiveProfileUsernameDisplay))]
        [NotifyPropertyChangedFor(nameof(ActiveProfileAccountTypeDisplay))]
        [NotifyPropertyChangedFor(nameof(HasActiveProfile))]
        private UserProfile? _activeProfile;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(ActiveInstanceHeaderStatus))]
        [NotifyPropertyChangedFor(nameof(ActiveInstanceSubtitle))]
        [NotifyPropertyChangedFor(nameof(PlayButtonMainText))]
        [NotifyPropertyChangedFor(nameof(CanPlay))]
        private MinecraftInstance? _activeInstance;

        [ObservableProperty]
        private string _selectedContentTab = "UpdateNews"; // "UpdateNews", "LauncherLog", "CrashReport"

        [ObservableProperty]
        private bool _hasCrash = false;

        [ObservableProperty]
        private string _crashReportLog = "No crash report available.";

        [ObservableProperty]
        private string _launcherLogs = string.Empty;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(PlayButtonMainText))]
        private bool _isLaunching = false;

        [ObservableProperty]
        private bool _isUpdateAvailable;

        [ObservableProperty]
        private string _latestUpdateVersion = string.Empty;

        [ObservableProperty]
        private string _updateNotes = string.Empty;

        [ObservableProperty]
        private bool _isUpdating;

        [ObservableProperty]
        private double _updateProgress;

        [ObservableProperty]
        private string _updateStatus = string.Empty;

        public ObservableCollection<MinecraftInstance> Instances { get; } = new();
        public ObservableCollection<NotificationInfo> Notifications { get; } = new();

        // Page ViewModels (Lazy initialized on demand to guarantee zero UI blocking at startup)
        private HomeViewModel? _homeVM;
        public HomeViewModel HomeVM => _homeVM ??= new HomeViewModel(this);

        private AccountsViewModel? _accountsVM;
        public AccountsViewModel AccountsVM => _accountsVM ??= new AccountsViewModel(this);

        private VersionsViewModel? _versionsVM;
        public VersionsViewModel VersionsVM => _versionsVM ??= new VersionsViewModel(this);

        private ModsViewModel? _modsVM;
        public ModsViewModel ModsVM => _modsVM ??= new ModsViewModel(this);

        private SettingsViewModel? _settingsVM;
        public SettingsViewModel SettingsVM => _settingsVM ??= new SettingsViewModel(this);

        private InstallationManagerViewModel? _installationManagerVM;
        public InstallationManagerViewModel InstallationManagerVM => _installationManagerVM ??= new InstallationManagerViewModel(this);

        private readonly IInstanceService? _instanceService;
        private readonly IAccountService? _accountService;

        public MainViewModel()
        {
            CurrentPageViewModel = HomeVM;

            // Live real launcher logs
            LauncherLogs = CrashLogger.GetLiveLogsText();
            CrashLogger.LogsUpdated += () =>
            {
                Dispatch(() =>
                {
                    LauncherLogs = CrashLogger.GetLiveLogsText();
                });
            };

            try
            {
                _accountService = ServiceLocator.Resolve<IAccountService>();
                _instanceService = ServiceLocator.Resolve<IInstanceService>();

                ActiveProfile = _accountService.ActiveProfile;
                _accountService.ActiveProfileChanged += p =>
                {
                    Dispatch(() =>
                    {
                        ActiveProfile = p;
                        OnPropertyChanged(nameof(ActiveProfile));
                        OnPropertyChanged(nameof(ActiveProfileUsernameDisplay));
                        OnPropertyChanged(nameof(ActiveProfileAccountTypeDisplay));
                        OnPropertyChanged(nameof(HasActiveProfile));
                    });
                };

                LoadInstances();
                StartupProfiler.Record("Instances loaded");

                _instanceService.InstancesChanged += () =>
                {
                    Dispatch(LoadInstances);
                };

                CrashLogger.LogMessage($"[VayuClient]: Session initialized. Developer: {AppInfo.DeveloperName} (Version {AppInfo.VersionString})");
                CrashLogger.LogMessage($"[InstanceService]: Loaded {Instances.Count} instance(s) from %APPDATA%\\VayuClient\\Instances");
                if (ActiveProfile != null)
                {
                    CrashLogger.LogMessage($"[ProfileService]: Authenticated profile: {ActiveProfile.Username}");
                }
                if (ActiveInstance != null)
                {
                    CrashLogger.LogMessage($"[InstanceService]: Selected active instance: {ActiveInstance.Name} ({ActiveInstance.DisplaySubtitle})");
                }
                CrashLogger.LogMessage("[Launcher]: Ready for game execution.");

                try
                {
                    var discordRpc = ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>();
                    discordRpc?.SetInLauncherPresence();
                }
                catch { }

                // Background GitHub Auto-Update Check
                _ = Task.Run(async () =>
                {
                    await Task.Delay(2500);
                    try
                    {
                        var updateService = ServiceLocator.Resolve<IUpdateService>();
                        if (updateService != null)
                        {
                            var res = await updateService.CheckForUpdatesAsync();
                            if (res.IsUpdateAvailable)
                            {
                                Dispatch(() =>
                                {
                                    IsUpdateAvailable = true;
                                    LatestUpdateVersion = res.LatestVersion;
                                    UpdateNotes = res.ReleaseNotes;
                                    ShowNotification("Update Available", $"VayuClient v{res.LatestVersion} is available! Click to update directly.", NotificationType.Info);
                                });
                            }
                        }
                    }
                    catch { }
                });

                // Automated Startup Java 21 & 25 Pre-Provisioning (Ensures Java 25 and 21 are immediately available on all PCs)
                _ = Task.Run(async () =>
                {
                    try
                    {
                        var javaService = ServiceLocator.Resolve<Services.Java.IJavaRuntimeService>();
                        if (javaService != null)
                        {
                            if (javaService.FindCompatibleRuntime(25) == null)
                            {
                                CrashLogger.LogMessage("[JavaService]: Pre-installing required Java 25 runtime for Minecraft 26+...");
                                await javaService.EnsureJavaRuntimeAsync(25);
                                CrashLogger.LogMessage("[JavaService]: Java 25 runtime successfully installed and ready.");
                            }

                            if (javaService.FindCompatibleRuntime(21) == null)
                            {
                                CrashLogger.LogMessage("[JavaService]: Pre-installing required Java 21 LTS runtime...");
                                await javaService.EnsureJavaRuntimeAsync(21);
                                CrashLogger.LogMessage("[JavaService]: Java 21 LTS runtime successfully installed and ready.");
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        CrashLogger.LogException("Background Java Runtime Pre-Provisioning", ex);
                    }
                });
            }
            catch { /* ServiceLocator during design time */ }
        }

        [RelayCommand]
        public async Task ApplyUpdateAsync()
        {
            var updateService = ServiceLocator.Resolve<IUpdateService>();
            var updateInfo = updateService?.LatestUpdateInfo;
            if (updateService == null || updateInfo == null || !updateInfo.IsUpdateAvailable)
            {
                ShowNotification("Updates", "No pending update found.", NotificationType.Info);
                return;
            }

            try
            {
                IsUpdating = true;
                UpdateStatus = $"Downloading v{updateInfo.LatestVersion}...";
                var progress = new Progress<DownloadProgressInfo>(p =>
                {
                    Dispatch(() =>
                    {
                        UpdateProgress = p.Percentage;
                        UpdateStatus = $"Downloading update: {p.Percentage:F0}% ({p.SpeedMBPerSec:F1} MB/s)";
                    });
                });

                await updateService.DownloadAndInstallUpdateAsync(updateInfo, progress);
            }
            catch (Exception ex)
            {
                IsUpdating = false;
                ShowNotification("Update Error", $"Could not complete update: {ex.Message}", NotificationType.Error);
            }
        }

        public string AppVersionDisplay => AppInfo.DisplayVersion;
        public string AppFullName => AppInfo.FullVersionName;
        public string DeveloperName => AppInfo.DeveloperName;
        public string WindowTitle => $"{AppInfo.AppName} {AppInfo.VersionString}";

        public string ActiveProfileUsernameDisplay => ActiveProfile != null ? ActiveProfile.Username : "No Profile";
        public string ActiveProfileAccountTypeDisplay => ActiveProfile != null ? ActiveProfile.AccountTypeDisplay : "Not Logged In";
        public bool HasActiveProfile => ActiveProfile != null;

        public string ActiveInstanceHeaderStatus => ActiveInstance != null
            ? $"Active: {ActiveInstance.Name} ({ActiveInstance.Loader} {ActiveInstance.MinecraftVersion})"
            : "No instance selected";

        public string ActiveInstanceSubtitle => ActiveInstance != null
            ? $"{ActiveInstance.MinecraftVersion} ({ActiveInstance.Loader}) • {ActiveInstance.DisplayRam}"
            : (HasInstances ? "Select an instance" : "No instance installed");

        public bool HasInstances => Instances.Count > 0;
        public bool CanPlay => true;

        public string PlayButtonMainText
        {
            get
            {
                if (IsLaunching) return "STARTING...";
                if (ActiveInstance == null) return "CREATE INSTANCE";
                return "▶ PLAY";
            }
        }

        public void LoadInstances()
        {
            if (_instanceService == null) return;
            var list = _instanceService.GetAllInstances()?.ToList() ?? new List<MinecraftInstance>();
            var active = _instanceService.GetActiveInstance();

            Dispatch(() =>
            {
                Instances.Clear();
                foreach (var inst in list)
                {
                    inst.IsActive = (active != null && inst.InstanceId == active.InstanceId);
                    Instances.Add(inst);
                }
                ActiveInstance = active ?? Instances.FirstOrDefault();
                OnPropertyChanged(nameof(HasInstances));
                OnPropertyChanged(nameof(ActiveInstanceSubtitle));
                OnPropertyChanged(nameof(ActiveInstanceHeaderStatus));
                OnPropertyChanged(nameof(PlayButtonMainText));
            });
        }

        private static void Dispatch(Action action)
        {
            var app = Application.Current;
            if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess() && !app.Dispatcher.HasShutdownStarted)
            {
                try
                {
                    app.Dispatcher.BeginInvoke(action);
                }
                catch
                {
                    action();
                }
            }
            else
            {
                action();
            }
        }

        [RelayCommand]
        public void SelectInstance(MinecraftInstance? instance)
        {
            if (instance == null || _instanceService == null) return;
            _instanceService.SetActiveInstance(instance.InstanceId);
            ActiveInstance = instance;
            OnPropertyChanged(nameof(ActiveInstanceSubtitle));

            // Return to Home / content view if currently in another page
            if (CurrentPage != "Home")
            {
                NavigateTo("Home");
            }
        }

        [RelayCommand]
        public void NavigateTo(object? pageObj)
        {
            var page = pageObj?.ToString() ?? "Home";

            try
            {
                CrashLogger.CurrentPage = page;
                CurrentPage = page;

                if (page == "Mods") ModsVM.LoadInstances();
                else if (page == "Accounts") AccountsVM.LoadProfiles();
                else if (page == "Home") HomeVM.RefreshProfile();
                else if (page == "InstallationManager") InstallationManagerVM.LoadInstallations();

                CurrentPageViewModel = page switch
                {
                    "Home" => HomeVM,
                    "Accounts" => AccountsVM,
                    "Versions" => VersionsVM,
                    "Mods" => ModsVM,
                    "Settings" => SettingsVM,
                    "InstallationManager" => InstallationManagerVM,
                    _ => HomeVM
                };
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("MainViewModel.NavigateTo", ex, page);
                ShowNotification("Navigation Error", $"Unable to navigate to '{page}': {ex.Message}", NotificationType.Error);
                CurrentPage = "Home";
                CurrentPageViewModel = HomeVM;
            }
        }

        [RelayCommand]
        public void OpenSettings(object? tab)
        {
            if (tab is string tabName && !string.IsNullOrEmpty(tabName))
            {
                SettingsVM.ActiveTab = (tabName == "General") ? "Performance" : tabName;
            }
            else
            {
                SettingsVM.ActiveTab = "Performance";
            }
            NavigateTo("Settings");
        }

        [RelayCommand]
        public void OpenInstallationManager()
        {
            NavigateTo("InstallationManager");
        }

        [RelayCommand]
        public void OpenAbout()
        {
            SettingsVM.ActiveTab = "About";
            NavigateTo("Settings");
        }

        [RelayCommand]
        public void SelectContentTab(object? tab)
        {
            if (tab is string s)
            {
                SelectedContentTab = s;
            }
        }

        [RelayCommand]
        public void Play()
        {
            if (ActiveInstance == null)
            {
                ShowNotification("No Instance Selected", "Please select or create a Minecraft instance first.", NotificationType.Info);
                NavigateTo("Versions");
                return;
            }

            try
            {
                IsLaunching = true;
                ShowNotification("Launching Game", $"Starting {ActiveInstance.Name} ({ActiveInstanceSubtitle})...", NotificationType.Info);
                
                var launchService = ServiceLocator.Resolve<ILaunchService>();

                // Execute launch in background
                _ = Task.Run(async () =>
                {
                    try
                    {
                        var ok = await launchService.LaunchInstanceAsync(ActiveInstance.InstanceId);
                        Dispatch(() =>
                        {
                            IsLaunching = false;
                            if (ok)
                            {
                                HasCrash = false;
                                ShowNotification("Game Started", $"{ActiveInstance.Name} is running.", NotificationType.Success);
                            }
                            else
                            {
                                HasCrash = true;
                                var errorMsg = !string.IsNullOrEmpty(launchService.StatusMessage) 
                                    ? launchService.StatusMessage 
                                    : "Could not start Minecraft instance.";
                                CrashReportLog = $"---- VayuClient Launch Error ----\nTime: {DateTime.Now}\nInstance: {ActiveInstance.Name} ({ActiveInstance.MinecraftVersion})\nError: {errorMsg}";
                                SelectedContentTab = "CrashReport";
                                ShowNotification("Launch Failed", errorMsg, NotificationType.Error);
                            }
                        });
                    }
                    catch (Exception ex)
                    {
                        Dispatch(() =>
                        {
                            IsLaunching = false;
                            HasCrash = true;
                            CrashReportLog = $"---- VayuClient Game Crash / Launch Error ----\nTime: {DateTime.Now}\nInstance: {ActiveInstance?.Name ?? "Unknown"}\nException: {ex.GetType().FullName}: {ex.Message}\n\nStack Trace:\n{ex.StackTrace}";
                            SelectedContentTab = "CrashReport";
                            ShowNotification("Launch Failed", ex.Message, NotificationType.Error);
                        });
                    }
                });
            }
            catch (Exception ex)
            {
                IsLaunching = false;
                ShowNotification("Launch Failed", ex.Message, NotificationType.Error);
            }
        }

        [RelayCommand]
        public void OpenInstanceFolder(MinecraftInstance? instance)
        {
            var inst = instance ?? ActiveInstance;
            if (inst == null) return;
            try
            {
                var dir = !string.IsNullOrEmpty(inst.GameDirectory) && Directory.Exists(inst.GameDirectory)
                    ? inst.GameDirectory
                    : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Instances");
                Directory.CreateDirectory(dir);
                Process.Start(new ProcessStartInfo("explorer.exe", dir) { UseShellExecute = true });
            }
            catch { }
        }

        [RelayCommand]
        public void DeleteInstance(MinecraftInstance? instance)
        {
            if (instance == null || _instanceService == null) return;
            var name = instance.Name;
            _instanceService.DeleteInstance(instance.InstanceId);
            ShowNotification("Instance Removed", $"Instance '{name}' has been deleted.", NotificationType.Info);
        }

        [RelayCommand]
        public void EditInstance(MinecraftInstance? instance)
        {
            if (instance != null)
            {
                SelectInstance(instance);
                InstallationManagerVM.EditInstance(instance);
            }
            NavigateTo("InstallationManager");
        }

        [RelayCommand]
        public void CreateNewInstance()
        {
            NavigateTo("Versions");
        }

        [RelayCommand]
        public void OpenCrashReportFile()
        {
            ShowNotification("Crash Report", "Opening crash-reports folder in Windows Explorer...", NotificationType.Info);
            try
            {
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var crashDir = System.IO.Path.Combine(appData, "VayuClient", "crash-reports");
                Directory.CreateDirectory(crashDir);
                Process.Start(new ProcessStartInfo("explorer.exe", crashDir) { UseShellExecute = true });
            }
            catch { }
        }

        [RelayCommand]
        public void GetCrashReportLink()
        {
            if (!string.IsNullOrWhiteSpace(CrashReportLog))
            {
                Clipboard.SetText(CrashReportLog);
                ShowNotification("Report Copied", "Crash report details copied to clipboard.", NotificationType.Success);
            }
            else
            {
                ShowNotification("No Crash Report", "There is no active crash report to copy.", NotificationType.Info);
            }
        }

        [RelayCommand]
        public void JoinDiscord()
        {
            try
            {
                Process.Start(new ProcessStartInfo("https://discord.gg/minecraft") { UseShellExecute = true });
            }
            catch { }
        }

        [RelayCommand]
        public void SupportPatreon()
        {
            try
            {
                Process.Start(new ProcessStartInfo("https://patreon.com/vayuclient") { UseShellExecute = true });
            }
            catch { }
        }

        [RelayCommand]
        private void SplashCompleted()
        {
            IsSplashVisible = false;
        }

        public void ShowNotification(string title, string message, NotificationType type = NotificationType.Info)
        {
            Dispatch(() =>
            {
                var notification = new NotificationInfo
                {
                    Title = title,
                    Message = message,
                    Type = type
                };
                Notifications.Add(notification);

                var timer = new System.Windows.Threading.DispatcherTimer
                {
                    Interval = TimeSpan.FromSeconds(notification.AutoDismissSeconds)
                };
                timer.Tick += (s, e) =>
                {
                    timer.Stop();
                    DismissNotification(notification);
                };
                timer.Start();
            });
        }

        [RelayCommand]
        private void DismissNotification(object? parameter)
        {
            Dispatch(() =>
            {
                if (parameter is NotificationInfo info)
                {
                    Notifications.Remove(info);
                }
                else if (parameter is string id)
                {
                    var notif = Notifications.ToList().FirstOrDefault(n => n.Id == id);
                    if (notif != null) Notifications.Remove(notif);
                }
                else
                {
                    var first = Notifications.ToList().FirstOrDefault();
                    if (first != null) Notifications.Remove(first);
                }
            });
        }
    }
}
