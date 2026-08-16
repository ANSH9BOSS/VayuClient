using System;
using System.IO;
using System.Collections.ObjectModel;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Instance;
using VayuClient.Services.Version;

namespace VayuClient.ViewModels
{
    public partial class VersionsViewModel : ObservableObject
    {
        private readonly MainViewModel _main;
        private List<MinecraftVersion> _rawManifestVersions = new();

        [ObservableProperty]
        private bool _isLoading;

        [ObservableProperty]
        private bool _hasError;

        [ObservableProperty]
        private string _errorMessage = string.Empty;

        [ObservableProperty]
        private string _searchQuery = string.Empty;

        [ObservableProperty]
        private string _filterType = "All"; // All, release, snapshot, old_beta, old_alpha

        [ObservableProperty]
        private string _sortOrder = "OldestFirst"; // OldestFirst, NewestFirst

        public ObservableCollection<MinecraftVersion> DisplayVersions { get; } = new();

        // ═══════════════════════════════════════════════
        // 5-STEP INSTANCE CREATION WIZARD STATE
        // ═══════════════════════════════════════════════

        [ObservableProperty]
        private bool _isWizardOpen;

        [ObservableProperty]
        private int _wizardStep = 1; // 1: Version & Name, 2: Loader, 3: RAM, 4: Modpack, 5: Summary

        public bool IsStep1 => WizardStep == 1;
        public bool IsStep2 => WizardStep == 2;
        public bool IsStep3 => WizardStep == 3;
        public bool IsStep4 => WizardStep == 4;
        public bool IsStep5 => WizardStep == 5;
        public bool IsPreviousVisible => WizardStep > 1;
        public string StepNumberDisplay => $"Step {WizardStep} of 5";
        public string NextButtonText => WizardStep == 5 ? "Create & Save Instance" : "Next Step →";

        partial void OnWizardStepChanged(int value)
        {
            OnPropertyChanged(nameof(IsStep1));
            OnPropertyChanged(nameof(IsStep2));
            OnPropertyChanged(nameof(IsStep3));
            OnPropertyChanged(nameof(IsStep4));
            OnPropertyChanged(nameof(IsStep5));
            OnPropertyChanged(nameof(IsPreviousVisible));
            OnPropertyChanged(nameof(StepNumberDisplay));
            OnPropertyChanged(nameof(NextButtonText));
        }

        // Step 1
        [ObservableProperty]
        private string _selectedMinecraftVersion = "1.21.4";

        // Step 2
        public ObservableCollection<LoaderInfo> AvailableLoaders { get; } = new();

        [ObservableProperty]
        private LoaderInfo? _selectedLoader;

        public ObservableCollection<string> AvailableLoaderVersions { get; } = new();

        [ObservableProperty]
        private string _selectedLoaderVersion = "None";

        // Step 3: RAM
        [ObservableProperty]
        private int _systemRamGB = 16;

        public int SystemRamMB => Math.Max(4096, SystemRamGB * 1024);

        [ObservableProperty]
        private int _recommendedRamMB = 4096;

        [ObservableProperty]
        private int _selectedRamMB = 4096;

        public string SelectedRamDisplay => $"{SelectedRamMB} MB ({(SelectedRamMB / 1024.0):F1} GB)";

        [ObservableProperty]
        private bool _isRamWarning;

        // Step 4: Modpack
        public ObservableCollection<ModpackInfo> ModrinthModpacks { get; } = new();

        [ObservableProperty]
        private ModpackInfo? _selectedModpack;

        [ObservableProperty]
        private string _modpackSearchQuery = string.Empty;

        [ObservableProperty]
        private bool _isModpackSearching;

        [ObservableProperty]
        private bool _isModpackIncompatible;

        [ObservableProperty]
        private string _modpackWarningText = string.Empty;

        // Step 5: Details
        [ObservableProperty]
        private string _instanceName = "My Minecraft Instance";

        [ObservableProperty]
        private string _instanceIcon = "🎮";

        [ObservableProperty]
        private string _gameDirPreview = string.Empty;

        public string[] AvailableIcons { get; } = new[]
        {
            "🎮", "⚔️", "🛡️", "🚀", "💎", "⛏️", "🌟", "🧙", "🧭", "🏰"
        };

        public VersionsViewModel(MainViewModel main)
        {
            _main = main;

            // System RAM detection
            try
            {
                var gcMemoryInfo = GC.GetGCMemoryInfo();
                long totalBytes = gcMemoryInfo.TotalAvailableMemoryBytes;
                if (totalBytes > 0)
                {
                    SystemRamGB = Math.Max(4, (int)(totalBytes / (1024 * 1024 * 1024)));
                }
            }
            catch
            {
                SystemRamGB = 16;
            }

            _selectedRamMB = Math.Min(4096, SystemRamMB);
            _recommendedRamMB = 4096;

            _ = LoadVersionsAsync();
        }

        partial void OnSelectedRamMBChanged(int value)
        {
            OnPropertyChanged(nameof(SelectedRamDisplay));
            IsRamWarning = value > (int)(SystemRamMB * 0.85);
        }

        partial void OnInstanceNameChanged(string value)
        {
            UpdateGameDirPreview();
        }

        private void UpdateGameDirPreview()
        {
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var safeName = string.Join("_", InstanceName.Split(Path.GetInvalidFileNameChars())).Trim();
            if (string.IsNullOrEmpty(safeName)) safeName = "Unnamed_Instance";
            GameDirPreview = Path.Combine(appData, "VayuClient", "Instances", safeName, "game");
        }

        [RelayCommand]
        public async Task LoadVersionsAsync()
        {
            IsLoading = true;
            HasError = false;
            ErrorMessage = string.Empty;

            try
            {
                var versionService = ServiceLocator.Resolve<IVersionService>();
                _rawManifestVersions = await versionService.GetManifestVersionsAsync(forceRefresh: false);
                if (!string.IsNullOrEmpty(versionService.LatestRelease))
                {
                    SelectedMinecraftVersion = versionService.LatestRelease;
                }
                ApplyFilterAndSort();
            }
            catch (Exception ex)
            {
                HasError = true;
                ErrorMessage = ex.Message;
                _main.ShowNotification("Network Error", "Unable to load Minecraft versions manifest.", NotificationType.Error);
            }
            finally
            {
                IsLoading = false;
            }
        }

        [RelayCommand]
        private void SetFilter(object? filterObj)
        {
            FilterType = filterObj?.ToString() ?? "All";
            ApplyFilterAndSort();
        }

        [RelayCommand]
        private void ToggleSortOrder()
        {
            SortOrder = (SortOrder == "OldestFirst") ? "NewestFirst" : "OldestFirst";
            ApplyFilterAndSort();
        }

        partial void OnSearchQueryChanged(string value)
        {
            ApplyFilterAndSort();
        }

        private void ApplyFilterAndSort()
        {
            if (_rawManifestVersions == null || _rawManifestVersions.Count == 0)
            {
                Dispatch(() => DisplayVersions.Clear());
                return;
            }

            IEnumerable<MinecraftVersion> query = _rawManifestVersions;

            // Filter
            if (!string.Equals(FilterType, "All", StringComparison.OrdinalIgnoreCase))
            {
                query = query.Where(v => string.Equals(v.Type, FilterType, StringComparison.OrdinalIgnoreCase));
            }

            // Search
            if (!string.IsNullOrWhiteSpace(SearchQuery))
            {
                var q = SearchQuery.Trim().ToLowerInvariant();
                query = query.Where(v => v.Id.ToLowerInvariant().Contains(q) || v.DisplayType.ToLowerInvariant().Contains(q));
            }

            // Ordering: Oldest → Newest vs Newest → Oldest
            var list = query.ToList();
            if (SortOrder == "NewestFirst")
            {
                list.Sort((a, b) => MinecraftVersionComparer.Instance.Compare(b, a));
            }
            else
            {
                list.Sort(MinecraftVersionComparer.Instance);
            }

            Dispatch(() =>
            {
                DisplayVersions.Clear();
                foreach (var v in list)
                {
                    DisplayVersions.Add(v);
                }
            });
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

        // ═══════════════════════════════════════════════
        // WIZARD NAVIGATION & LOGIC
        // ═══════════════════════════════════════════════

        [RelayCommand]
        public async Task StartCreateInstance(object? versionIdObj)
        {
            var versionId = versionIdObj?.ToString() ?? "1.21.4";
            SelectedMinecraftVersion = versionId;
            InstanceName = $"Minecraft {versionId}";
            WizardStep = 1;
            IsWizardOpen = true;
            await LoadLoadersForVersionAsync();
        }

        [RelayCommand]
        public async Task NextStep()
        {
            if (WizardStep < 5)
            {
                await GoToStep(WizardStep + 1);
            }
            else
            {
                await ConfirmCreateInstance();
            }
        }

        [RelayCommand]
        public async Task PreviousStep()
        {
            if (WizardStep > 1)
            {
                await GoToStep(WizardStep - 1);
            }
        }

        [RelayCommand]
        public async Task GoToStep(object? stepObj)
        {
            int step = 1;
            if (stepObj is int s)
            {
                step = s;
            }
            else if (stepObj != null && int.TryParse(stepObj.ToString(), out int parsed))
            {
                step = parsed;
            }

            WizardStep = Math.Clamp(step, 1, 5);

            if (WizardStep == 2) // Loader Selection
            {
                if (AvailableLoaders.Count == 0)
                {
                    await LoadLoadersForVersionAsync();
                }
            }
            else if (WizardStep == 3) // RAM
            {
                // Set intelligent recommended RAM based on loader
                bool isModded = SelectedLoader != null && !SelectedLoader.Name.Equals("Vanilla", StringComparison.OrdinalIgnoreCase);
                RecommendedRamMB = isModded ? Math.Min(6144, SystemRamMB) : Math.Min(4096, SystemRamMB);

                // Preserve user-selected RAM if already valid, otherwise use recommended
                if (SelectedRamMB < 1024 || SelectedRamMB > SystemRamMB)
                {
                    SelectedRamMB = RecommendedRamMB;
                }
            }
            else if (WizardStep == 4) // Modpack Search
            {
                if (ModrinthModpacks.Count == 0)
                {
                    await LoadModpacksAsync();
                }
            }
            else if (WizardStep == 5) // Instance Summary
            {
                if (string.IsNullOrWhiteSpace(InstanceName) || InstanceName == "My Minecraft Instance")
                {
                    var loaderName = SelectedLoader?.Name ?? "Vanilla";
                    InstanceName = $"Minecraft {SelectedMinecraftVersion} ({loaderName})";
                }
                UpdateGameDirPreview();
            }
        }

        [RelayCommand]
        public void SetRamPreset(object? mbObj)
        {
            int mb = 4096;
            if (mbObj is int m)
            {
                mb = m;
            }
            else if (mbObj != null && int.TryParse(mbObj.ToString(), out int parsed))
            {
                mb = parsed;
            }

            SelectedRamMB = Math.Clamp(mb, 1024, SystemRamMB);
        }

        [RelayCommand]
        public void SelectIcon(object? iconObj)
        {
            InstanceIcon = iconObj?.ToString() ?? "🎮";
        }

        private async Task LoadLoadersForVersionAsync()
        {
            try
            {
                var versionService = ServiceLocator.Resolve<IVersionService>();
                var loaders = await versionService.GetCompatibleLoadersAsync(SelectedMinecraftVersion);

                Dispatch(() =>
                {
                    AvailableLoaders.Clear();
                    foreach (var l in loaders)
                    {
                        AvailableLoaders.Add(l);
                    }

                    // Default to Vanilla
                    SelectedLoader = AvailableLoaders.FirstOrDefault(l => l.Name == "Vanilla") ?? AvailableLoaders.FirstOrDefault();
                });

                if (SelectedLoader != null)
                {
                    await SelectLoader(SelectedLoader);
                }
            }
            catch { }
        }

        [RelayCommand]
        public async Task SelectLoader(LoaderInfo? loader)
        {
            if (loader == null || !loader.IsCompatible) return;
            SelectedLoader = loader;

            try
            {
                var versionService = ServiceLocator.Resolve<IVersionService>();
                var versions = await versionService.GetLoaderVersionsAsync(loader.Name, SelectedMinecraftVersion);

                Dispatch(() =>
                {
                    AvailableLoaderVersions.Clear();
                    foreach (var v in versions)
                    {
                        AvailableLoaderVersions.Add(v);
                    }

                    SelectedLoaderVersion = AvailableLoaderVersions.FirstOrDefault() ?? "None";
                });
            }
            catch { }
        }

        private async Task LoadModpacksAsync()
        {
            IsModpackSearching = true;

            try
            {
                var versionService = ServiceLocator.Resolve<IVersionService>();
                var loaderName = SelectedLoader?.Name ?? "Vanilla";

                var packs = await versionService.SearchModrinthModpacksAsync(ModpackSearchQuery, SelectedMinecraftVersion, loaderName);
                
                Dispatch(() =>
                {
                    ModrinthModpacks.Clear();
                    foreach (var p in packs)
                    {
                        ModrinthModpacks.Add(p);
                    }

                    SelectedModpack = ModrinthModpacks.FirstOrDefault();
                    if (SelectedModpack != null)
                    {
                        SelectModpack(SelectedModpack);
                    }
                });
            }
            catch { }
            finally
            {
                IsModpackSearching = false;
            }
        }

        [RelayCommand]
        public async Task SearchModpacks()
        {
            await LoadModpacksAsync();
        }

        [RelayCommand]
        public void SelectModpack(ModpackInfo modpack)
        {
            SelectedModpack = modpack;
            IsModpackIncompatible = !modpack.IsCompatible;
            ModpackWarningText = modpack.CompatibilityWarning;
        }

        [RelayCommand]
        public void CloseWizard()
        {
            IsWizardOpen = false;
        }

        [RelayCommand]
        public async Task ConfirmCreateInstance()
        {
            if (string.IsNullOrWhiteSpace(InstanceName))
            {
                _main.ShowNotification("Invalid Name", "Instance name cannot be empty.", NotificationType.Warning);
                return;
            }

            try
            {
                var instanceService = ServiceLocator.Resolve<IInstanceService>();
                var instance = new MinecraftInstance
                {
                    InstanceId = Guid.NewGuid().ToString("N"),
                    Name = InstanceName.Trim(),
                    Icon = InstanceIcon,
                    MinecraftVersion = SelectedMinecraftVersion,
                    Loader = SelectedLoader?.Name ?? "Vanilla",
                    LoaderVersion = SelectedLoaderVersion,
                    RamMB = SelectedRamMB,
                    IsActive = true,
                    ModpackId = (SelectedModpack != null && SelectedModpack.Id != "none") ? SelectedModpack.Id : null,
                    ModpackVersion = (SelectedModpack != null && SelectedModpack.Id != "none") ? SelectedModpack.LatestVersion : null,
                };

                await instanceService.CreateInstanceAsync(instance);
                instanceService.SetActiveInstance(instance.InstanceId);

                IsWizardOpen = false;

                _main.ShowNotification(
                    "Instance Created",
                    $"'{instance.Name}' is now your active instance. Ready to play!",
                    NotificationType.Success);

                _main.NavigateToCommand.Execute("Home");
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Error", $"Failed to create instance: {ex.Message}", NotificationType.Error);
            }
        }
    }
}
