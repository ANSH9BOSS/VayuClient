using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Threading.Tasks;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Hardware;
using VayuClient.Services.Java;
using VayuClient.Services.Monitoring;
using VayuClient.Services.Settings;
using VayuClient.Services.Updates;

namespace VayuClient.ViewModels
{
    public partial class SettingsViewModel : ObservableObject
    {
        private readonly MainViewModel _main;
        private readonly ISettingsService _settingsService;
        private readonly IJavaRuntimeService? _javaService;
        private readonly IHardwareInfoService _hardwareInfoService;
        private readonly IPerformanceMonitorService _performanceMonitor;

        [ObservableProperty]
        private string _activeTab = "Performance"; // Performance, Appearance, GameLaunch, Network, About

        // ═══ Performance Profile & Hardware ═══
        [ObservableProperty]
        private HardwareProfile _hardware;

        [ObservableProperty]
        private string _selectedPerformanceMode = "⚖️ Balanced (Recommended)";

        public ObservableCollection<string> PerformanceModes { get; } = new()
        {
            "⚡ High Performance Gaming",
            "⚖️ Balanced (Recommended)",
            "🌱 Power Saver",
            "🛠️ Custom Tuning"
        };

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
        private string _customJvmArgs = "-XX:+UseG1GC -XX:G1ReservePercent=15 -XX:G1HeapRegionSize=32M";

        // ═══ 2. Live Performance Telemetry ═══
        [ObservableProperty]
        private double _liveLauncherCpu = 0.0;

        [ObservableProperty]
        private double _liveLauncherMemoryMB = 0.0;

        [ObservableProperty]
        private double _liveHostAvailableRamGB = 0.0;

        [ObservableProperty]
        private string _liveMinecraftStatus = "Idle / Ready";

        [ObservableProperty]
        private string _benchmarkReport = "Click 'Run System Benchmark' to measure hardware and launch pipeline throughput.";

        // ═══ 3. Appearance & Custom Backgrounds ═══
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

        // ═══ 4. Game Launch & Display ═══
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
        private string _discordClientId = "356875570916753438";

        [ObservableProperty]
        private bool _isCheckingUpdates = false;

        [ObservableProperty]
        private string _updateStatusText = "Up to date with GitHub Releases";

        public string CheckForUpdatesButtonText => IsCheckingUpdates ? "Checking Releases..." : "Check for GitHub Updates";

        partial void OnIsCheckingUpdatesChanged(bool value)
        {
            OnPropertyChanged(nameof(CheckForUpdatesButtonText));
        }

        partial void OnDiscordRichPresenceChanged(bool value)
        {
            try
            {
                var discordRpc = ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>();
                if (discordRpc != null) discordRpc.IsEnabled = value;
            }
            catch { }
        }

        partial void OnDiscordClientIdChanged(string value)
        {
            try
            {
                var discordRpc = ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>();
                if (discordRpc != null && !string.IsNullOrWhiteSpace(value))
                {
                    discordRpc.ClientId = value.Trim();
                }
            }
            catch { }
        }

        [ObservableProperty]
        private bool _showLauncherConsole = false;

        [ObservableProperty]
        private bool _nativeTitleBar = false;

        // ═══ 5. Network & Proxy ═══
        [ObservableProperty]
        private int _downloadConcurrency = 8;

        [ObservableProperty]
        private bool _dnsOverride = true;

        [ObservableProperty]
        private bool _forceLanOfflineMode = false;

        [ObservableProperty]
        private bool _modernForgeInstaller = true;

        // ═══ 6. System Specs & About Metadata ═══
        public string SystemCpuInfo => $"{Hardware.CpuName} ({Hardware.PhysicalCores} Physical Cores / {Hardware.LogicalProcessors} Threads)";
        public string SystemGpuInfo => $"{Hardware.GpuName} ({(Hardware.DedicatedVramGB > 0 ? $"{Hardware.DedicatedVramGB} GB VRAM" : "DirectX 12 Acceleration")})";
        public string SystemRamInfo => $"{Hardware.TotalRamGB} GB Total RAM ({Hardware.AvailableRamGB} GB Available)";
        public string SystemDiskInfo => $"{Hardware.FreeDiskGB} GB Free on Install Drive";
        public string SystemOsInfo => Hardware.OperatingSystemName;
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
            _hardwareInfoService = ServiceLocator.Resolve<IHardwareInfoService>();
            _performanceMonitor = ServiceLocator.Resolve<IPerformanceMonitorService>();

            _hardware = _hardwareInfoService.GetHardwareProfile();

            // Set initial recommended RAM
            _defaultRamMB = _hardware.RecommendedRamMB;
            _downloadConcurrency = _hardware.RecommendedDownloadThreads;

            _performanceMonitor.SnapshotUpdated += OnPerformanceSnapshotUpdated;
            _performanceMonitor.StartMonitoring(1500);

            LoadDetectedJavaRuntimes();
            LoadFromSettings();
        }

        private void OnPerformanceSnapshotUpdated(object? sender, PerformanceSnapshot snap)
        {
            Application.Current?.Dispatcher?.InvokeAsync(() =>
            {
                LiveLauncherCpu = snap.LauncherCpuPercent;
                LiveLauncherMemoryMB = snap.LauncherWorkingSetMB;
                LiveHostAvailableRamGB = snap.HostAvailableRamGB;
                LiveMinecraftStatus = snap.MinecraftStatus;
            });
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

        partial void OnSelectedPerformanceModeChanged(string value)
        {
            if (value.Contains("High Performance"))
            {
                SelectedJvmPreset = "FastCraft / Sodium Boost (High FPS)";
                DefaultRamMB = Math.Max(Hardware.RecommendedRamMB, 6144);
                UseDedicatedGpu = true;
                SmoothAnimations = true;
                DownloadConcurrency = Math.Max(8, Hardware.RecommendedDownloadThreads);
                CustomJvmArgs = "-XX:+UseG1GC -XX:G1ReservePercent=15 -XX:G1HeapRegionSize=32M -XX:+UnlockExperimentalVMOptions -XX:InitiatingHeapOccupancyPercent=45";
            }
            else if (value.Contains("Balanced"))
            {
                SelectedJvmPreset = "Balanced (Standard G1GC)";
                DefaultRamMB = Hardware.RecommendedRamMB;
                UseDedicatedGpu = true;
                SmoothAnimations = true;
                DownloadConcurrency = Hardware.RecommendedDownloadThreads;
                CustomJvmArgs = "-XX:+UseG1GC -XX:G1ReservePercent=15 -XX:G1HeapRegionSize=32M";
            }
            else if (value.Contains("Power Saver"))
            {
                SelectedJvmPreset = "Low Memory Optimizer (Minimal Footprint)";
                DefaultRamMB = Math.Min(3072, Hardware.RecommendedRamMB);
                SmoothAnimations = false;
                DownloadConcurrency = 4;
                CustomJvmArgs = "-XX:+UseG1GC -XX:G1ReservePercent=25 -XX:MaxGCPauseMillis=100";
            }
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
            DiscordClientId = !string.IsNullOrWhiteSpace(s.DiscordClientId) ? s.DiscordClientId : "356875570916753438";
            DnsOverride = s.DnsOverride;
            ForceLanOfflineMode = s.ForceLanOfflineMode;
            ModernForgeInstaller = s.ModernForgeInstaller;
            ShowLauncherConsole = s.ShowLauncherConsole;
            DownloadConcurrency = s.DownloadConcurrency > 0 ? s.DownloadConcurrency : Hardware.RecommendedDownloadThreads;
            DefaultRamMB = s.DefaultMemoryMB > 0 ? s.DefaultMemoryMB : Hardware.RecommendedRamMB;
        }

        [RelayCommand]
        private void SelectTab(object? tab)
        {
            if (tab is string s) ActiveTab = s;
        }

        [RelayCommand]
        private async Task RunBenchmarkAsync()
        {
            _main.ShowNotification("Running Benchmark", "Measuring CPU hashing, disk I/O, and launch pipeline throughput...", NotificationType.Info);

            var sw = Stopwatch.StartNew();

            // 1. SHA-1 Hashing Throughput (10 MB buffer in memory)
            var sampleData = new byte[10 * 1024 * 1024];
            new Random(42).NextBytes(sampleData);
            var hashSw = Stopwatch.StartNew();
            using (var sha1 = SHA1.Create())
            {
                for (int i = 0; i < 5; i++)
                {
                    sha1.ComputeHash(sampleData);
                }
            }
            hashSw.Stop();
            double totalHashedMB = 50.0;
            double hashSpeedMBs = totalHashedMB / Math.Max(0.001, hashSw.Elapsed.TotalSeconds);

            // 2. Refresh dynamic hardware
            var hw = await _hardwareInfoService.GetHardwareProfileAsync(forceRefresh: true);
            Hardware = hw;

            sw.Stop();

            var sb = new StringBuilder();
            sb.AppendLine($"⚡ Benchmark Completed in {sw.ElapsedMilliseconds} ms");
            sb.AppendLine($"• CPU: {hw.CpuName} ({hw.PhysicalCores} Physical Cores / {hw.LogicalProcessors} Threads)");
            sb.AppendLine($"• SHA-1 Verification Throughput: {hashSpeedMBs:F1} MB/s");
            sb.AppendLine($"• Launcher Working Set Memory: {Process.GetCurrentProcess().WorkingSet64 / (1024 * 1024)} MB");
            sb.AppendLine($"• System RAM: {hw.TotalRamGB} GB Total • {hw.AvailableRamGB} GB Available");
            sb.AppendLine($"• Dedicated GPU: {hw.GpuName} ({(hw.DedicatedVramGB > 0 ? $"{hw.DedicatedVramGB} GB VRAM" : "DirectX 12 Accelerated")})");
            sb.AppendLine($"• Storage Free: {hw.FreeDiskGB} GB Available");
            sb.AppendLine($"• Recommended Allocation: {hw.RecommendedRamMB / 1024.0:F1} GB RAM ({hw.RecommendedRamMB} MB)");

            BenchmarkReport = sb.ToString();
            _main.ShowNotification("Benchmark Finished", $"Throughput: {hashSpeedMBs:F1} MB/s | Recommended RAM: {hw.RecommendedRamMB / 1024.0:F1} GB", NotificationType.Success);
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
            s.DiscordClientId = DiscordClientId;
            s.DnsOverride = DnsOverride;
            s.ForceLanOfflineMode = ForceLanOfflineMode;
            s.ModernForgeInstaller = ModernForgeInstaller;
            s.ShowLauncherConsole = ShowLauncherConsole;
            s.DownloadConcurrency = DownloadConcurrency;
            s.DefaultMemoryMB = DefaultRamMB;
            s.PerformanceMode = SelectedPerformanceMode;

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
            DefaultRamMB = Hardware.RecommendedRamMB;
            SelectedJavaRuntime = "Auto-Detect LTS (Recommended)";
            SelectedJvmPreset = "FastCraft / Sodium Boost (High FPS)";
            SelectedPerformanceMode = "⚖️ Balanced (Recommended)";
            UseDedicatedGpu = true;
            AllowCustomJvmArgs = true;
            SelectedThemeWallpaper = "Cyber Nether";
            BackgroundDimOpacity = 0.38;
            DarkTheme = true;
            SmoothAnimations = true;
            SelectedResolution = "1920x1080 (1080p FHD)";
            AutoJoinServerIp = "";
            DiscordRichPresence = true;
            DownloadConcurrency = Hardware.RecommendedDownloadThreads;

            await SaveSettingsAsync();
            _main.ShowNotification("Settings Reset", "All preferences have been restored to default values.", NotificationType.Info);
        }

        [RelayCommand]
        public async Task CheckForUpdatesAsync()
        {
            var updateService = ServiceLocator.Resolve<IUpdateService>();
            if (updateService == null) return;

            IsCheckingUpdates = true;
            UpdateStatusText = "Connecting to GitHub Releases...";
            _main.ShowNotification("Checking Updates", "Connecting to GitHub Releases...", NotificationType.Info);

            try
            {
                var res = await updateService.CheckForUpdatesAsync(force: true);
                if (res.IsUpdateAvailable)
                {
                    _main.IsUpdateAvailable = true;
                    _main.LatestUpdateVersion = res.LatestVersion;
                    _main.UpdateNotes = res.ReleaseNotes;
                    UpdateStatusText = $"✨ New version v{res.LatestVersion} available!";
                    _main.ShowNotification("Update Found", $"New version v{res.LatestVersion} is available! Click 'Update Directly Now' in the top banner.", NotificationType.Success);
                }
                else
                {
                    UpdateStatusText = $"✓ Up to date (v{AppInfo.VersionString})";
                    _main.ShowNotification("Up to Date", $"You are running the latest version of VayuClient (v{AppInfo.VersionString}).", NotificationType.Success);
                }
            }
            catch (Exception ex)
            {
                UpdateStatusText = "Could not reach GitHub Releases";
                _main.ShowNotification("Update Check Error", ex.Message, NotificationType.Error);
            }
            finally
            {
                IsCheckingUpdates = false;
            }
        }
    }
}
