using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Java;
using VayuClient.Services.Settings;
using VayuClient.Services.Updates;

namespace VayuClient.ViewModels
{
    public partial class SettingsViewModel : ObservableObject
    {
        private readonly MainViewModel _main;
        private readonly ISettingsService _settingsService;
        private readonly IJavaRuntimeService? _javaService;

        [ObservableProperty]
        private string _activeTab = "Performance"; // Performance, Appearance, GameLaunch, Network, About

        // ═══ 1. Performance & JVM ═══
        [ObservableProperty]
        private int _defaultRamMB = 4096;

        public string RamDisplay => $"{DefaultRamMB} MB ({DefaultRamMB / 1024.0:F1} GB)";

        [ObservableProperty]
        private string _selectedJavaRuntime = "Auto-Detect LTS (Recommended)";

        public ObservableCollection<string> AvailableJavaRuntimes { get; } = new();

        [ObservableProperty]
        private string _selectedJvmPreset = "FastCraft / Sodium Boost (High FPS)";

        public ObservableCollection<string> JvmPresets { get; } = new()
        {
            "FastCraft / Sodium Boost (High FPS)",
            "Aikar's Flags (Low Latency G1GC)",
            "Balanced (Standard G1GC)",
            "Low Memory Optimizer (Minimal Footprint)"
        };

        [ObservableProperty]
        private bool _useDedicatedGpu = true;

        [ObservableProperty]
        private bool _allowCustomJvmArgs = true;

        [ObservableProperty]
        private string _customJvmArgs = "-XX:+UseG1GC -XX:G1ReservePercent=20 -XX:G1HeapRegionSize=32M";

        // ═══ 2. Appearance & Custom Backgrounds ═══
        [ObservableProperty]
        private string _selectedThemeWallpaper = "Cyber Nether";

        public ObservableCollection<string> AvailableWallpapers { get; } = new()
        {
            "Cyber Nether",
            "Lush Caves",
            "Mountain Aurora",
            "Ocean Monument",
            "Cherry Grove",
            "Fantasy Sky Islands",
            "Epic Minecraft Hero"
        };

        [ObservableProperty]
        private double _backgroundDimOpacity = 0.38;

        [ObservableProperty]
        private bool _darkTheme = true;

        [ObservableProperty]
        private bool _smoothAnimations = true;

        [ObservableProperty]
        private string _selectedLanguage = "🇺🇸 English";

        public ObservableCollection<string> AvailableLanguages { get; } = new()
        {
            "🇺🇸 English", "🇪🇸 Español", "🇩🇪 Deutsch", "🇫🇷 Français", "🇵🇱 Polski", "🇷🇺 Русский"
        };

        // ═══ 3. Game Launch & Display ═══
        [ObservableProperty]
        private string _selectedResolution = "1920x1080 (1080p FHD)";

        public ObservableCollection<string> ResolutionPresets { get; } = new()
        {
            "1280x720 (720p HD)",
            "1920x1080 (1080p FHD)",
            "2560x1440 (1440p 2K QHD)",
            "3840x2160 (4K UHD)",
            "Fullscreen"
        };

        [ObservableProperty]
        private string _autoJoinServerIp = "";

        [ObservableProperty]
        private bool _discordRichPresence = true;

        [ObservableProperty]
        private bool _showLauncherConsole = false;

        [ObservableProperty]
        private bool _nativeTitleBar = false;

        // ═══ 4. Network & Proxy ═══
        [ObservableProperty]
        private int _downloadConcurrency = 8;

        [ObservableProperty]
        private bool _dnsOverride = true;

        [ObservableProperty]
        private bool _forceLanOfflineMode = false;

        [ObservableProperty]
        private bool _modernForgeInstaller = true;

        // ═══ 5. System Specs & About Metadata ═══
        public string SystemCpuInfo { get; private set; }
        public string SystemRamInfo { get; private set; }
        public string SystemOsInfo { get; private set; }
        public string AppName => AppInfo.AppName;
        public string AppVersion => $"{AppInfo.VersionString} (Production Release)";
        public string ReleaseInfo => "Official GitHub Release";
        public string DeveloperName => "ANSH9BOSS";
        public string Architecture => "Windows x64 (.NET 8.0 WPF Native)";
        public string GraphicsEngine => "DirectX 12 / DWM Hardware Composition";
        public string ModEngine => "Modrinth API & Mojang Manifest v2";

        public SettingsViewModel(MainViewModel main)
        {
            _main = main;
            _settingsService = ServiceLocator.Resolve<ISettingsService>();
            _javaService = ServiceLocator.Resolve<IJavaRuntimeService>();

            int cores = Environment.ProcessorCount;
            SystemCpuInfo = $"{Environment.GetEnvironmentVariable("PROCESSOR_IDENTIFIER") ?? "x64 Processor"} ({cores} Cores)";
            try
            {
                long totalMemBytes = (long)GC.GetGCMemoryInfo().TotalAvailableMemoryBytes;
                double totalGB = totalMemBytes / (1024.0 * 1024.0 * 1024.0);
                SystemRamInfo = $"{totalGB:F1} GB Total System RAM";
            }
            catch
            {
                SystemRamInfo = "16.0 GB Total System Memory";
            }
            SystemOsInfo = $"{Environment.OSVersion} (64-bit)";

            LoadDetectedJavaRuntimes();
            LoadFromSettings();
        }

        private void LoadDetectedJavaRuntimes()
        {
            AvailableJavaRuntimes.Clear();
            AvailableJavaRuntimes.Add("Auto-Detect LTS (Recommended)");

            try
            {
                if (_javaService != null)
                {
                    var runtimes = _javaService.DetectInstalledRuntimes();
                    foreach (var r in runtimes)
                    {
                        string desc = $"{r.Vendor} Java {r.MajorVersion} ({r.Version}) - {Path.GetFileName(r.Path)}";
                        if (!AvailableJavaRuntimes.Contains(desc))
                        {
                            AvailableJavaRuntimes.Add(desc);
                        }
                    }
                }
            }
            catch { }
        }

        partial void OnDefaultRamMBChanged(int value)
        {
            OnPropertyChanged(nameof(RamDisplay));
        }

        public void LoadFromSettings()
        {
            var s = _settingsService.Settings;
            SelectedLanguage = s.Language;
            DarkTheme = (s.Theme == "Dark");
            SmoothAnimations = s.SmoothAnimations;
            NativeTitleBar = s.NativeTitleBar;
            UseDedicatedGpu = s.UseDedicatedGpu;
            DiscordRichPresence = s.DiscordRichPresence;
            DnsOverride = s.DnsOverride;
            ForceLanOfflineMode = s.ForceLanOfflineMode;
            ModernForgeInstaller = s.ModernForgeInstaller;
            ShowLauncherConsole = s.ShowLauncherConsole;
        }

        [RelayCommand]
        private void SelectTab(object? tab)
        {
            if (tab is string s) ActiveTab = s;
        }

        [RelayCommand]
        private async Task SaveSettingsAsync()
        {
            var s = _settingsService.Settings;
            s.Language = SelectedLanguage;
            s.Theme = DarkTheme ? "Dark" : "Light";
            s.SmoothAnimations = SmoothAnimations;
            s.NativeTitleBar = NativeTitleBar;
            s.UseDedicatedGpu = UseDedicatedGpu;
            s.DiscordRichPresence = DiscordRichPresence;
            s.DnsOverride = DnsOverride;
            s.ForceLanOfflineMode = ForceLanOfflineMode;
            s.ModernForgeInstaller = ModernForgeInstaller;
            s.ShowLauncherConsole = ShowLauncherConsole;

            await _settingsService.SaveSettingsAsync(s);

            _main.ShowNotification("Preferences Saved", "Settings successfully saved to %APPDATA%\\VayuClient\\Settings.json.", NotificationType.Success);
            _main.NavigateTo("Home");
        }

        [RelayCommand]
        private void CloseSettings()
        {
            LoadFromSettings();
            _main.NavigateTo("Home");
        }

        [RelayCommand]
        private void OpenLauncherLog()
        {
            _main.NavigateTo("Home");
            _main.SelectedContentTab = "LauncherLog";
        }

        [RelayCommand]
        private void OpenAppDataFolder()
        {
            try
            {
                var appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient");
                Directory.CreateDirectory(appData);
                Process.Start(new ProcessStartInfo
                {
                    FileName = "explorer.exe",
                    Arguments = $"\"{appData}\"",
                    UseShellExecute = true
                });
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Folder Error", ex.Message, NotificationType.Error);
            }
        }

        [RelayCommand]
        private async Task ResetToDefaultsAsync()
        {
            DefaultRamMB = 4096;
            SelectedJavaRuntime = "Auto-Detect LTS (Recommended)";
            SelectedJvmPreset = "FastCraft / Sodium Boost (High FPS)";
            UseDedicatedGpu = true;
            AllowCustomJvmArgs = true;
            SelectedThemeWallpaper = "Cyber Nether";
            BackgroundDimOpacity = 0.38;
            DarkTheme = true;
            SmoothAnimations = true;
            SelectedResolution = "1920x1080 (1080p FHD)";
            AutoJoinServerIp = "";
            DiscordRichPresence = true;
            DownloadConcurrency = 8;

            await SaveSettingsAsync();
            _main.ShowNotification("Settings Reset", "All preferences have been restored to default values.", NotificationType.Info);
        }

        [RelayCommand]
        public async Task CheckForUpdatesAsync()
        {
            var updateService = ServiceLocator.Resolve<IUpdateService>();
            if (updateService == null) return;

            _main.ShowNotification("Checking Updates", "Connecting to GitHub Releases...", NotificationType.Info);
            var res = await updateService.CheckForUpdatesAsync(force: true);
            if (res.IsUpdateAvailable)
            {
                _main.IsUpdateAvailable = true;
                _main.LatestUpdateVersion = res.LatestVersion;
                _main.UpdateNotes = res.ReleaseNotes;
                _main.ShowNotification("Update Found", $"New version v{res.LatestVersion} is available! Click 'Update Directly Now' in the top banner.", NotificationType.Success);
            }
            else
            {
                _main.ShowNotification("Up to Date", $"You are running the latest version of VayuClient (v{AppInfo.VersionString}).", NotificationType.Success);
            }
        }
    }
}
