using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Instance;
using VayuClient.Services.Modpack;
using VayuClient.Services.Download;
using VayuClient.Services.Version;

namespace VayuClient.ViewModels
{
    public partial class InstallationManagerViewModel : ObservableObject, ILifecycleViewModel
    {
        private readonly MainViewModel _main;
        private readonly IInstanceService? _instanceService;
        private static readonly HttpClient _httpClient = new() { Timeout = TimeSpan.FromSeconds(15) };
        private bool _disposed;

        static InstallationManagerViewModel()
        {
            if (!_httpClient.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _httpClient.DefaultRequestHeaders.Add("User-Agent", AppInfo.UserAgent);
            }
        }

        [ObservableProperty]
        private string _activeTab = "Installations"; // Installations, Modpacks, Mods, Resource Packs, Shaders, Data Packs

        [ObservableProperty]
        private string _searchQuery = string.Empty;

        [ObservableProperty]
        private bool _isLoadingModrinth;

        [ObservableProperty]
        private string _statusMessage = string.Empty;

        [ObservableProperty]
        private int _currentPage = 1;

        [ObservableProperty]
        private int _totalPages = 1;

        [ObservableProperty]
        private int _totalHits = 0;

        [ObservableProperty]
        private string _paginationSummary = "Page 1 of 1";

        [ObservableProperty]
        private bool _canGoPrevious;

        [ObservableProperty]
        private bool _canGoNext;

        public ObservableCollection<int> PageNumbers { get; } = new();

        public const int PageSize = 10;

        public ObservableCollection<MinecraftInstance> Installations { get; } = new();
        public ObservableCollection<MinecraftInstance> FilteredInstallations { get; } = new();
        public ObservableCollection<ModpackInfo> ModrinthProjects { get; } = new();

        [ObservableProperty]
        private bool _hasInstallations;

        [ObservableProperty]
        private MinecraftInstance? _selectedInstallation;

        // ─── GAME LIBRARY FILTER & SORT ───
        [ObservableProperty]
        private string _libraryFilterLoader = "All"; // All, Fabric, Forge, NeoForge, Vanilla, Favorites

        [ObservableProperty]
        private string _librarySortBy = "Last Played"; // Last Played, Name, Version, RAM

        [ObservableProperty]
        private string _librarySearchQuery = string.Empty;

        public ObservableCollection<string> LibraryLoaders { get; } = new() { "All", "Fabric", "Forge", "NeoForge", "Vanilla", "Favorites" };
        public ObservableCollection<string> LibrarySortOptions { get; } = new() { "Last Played", "Name", "Version", "RAM" };

        // ─── INSTANCE INSPECTION DRAWER ───
        [ObservableProperty]
        private bool _isInspectOpen;

        [ObservableProperty]
        private MinecraftInstance? _inspectingInstance;

        // ─── 1. MODPACK CONFIGURATION MODAL STATE ───
        [ObservableProperty]
        private bool _isModpackModalOpen;

        [ObservableProperty]
        private ModpackInfo? _pendingModpack;

        [ObservableProperty]
        private string _modpackInstanceName = string.Empty;

        [ObservableProperty]
        private string _selectedModpackLoader = "Fabric";

        [ObservableProperty]
        private string _selectedModpackVersion = "1.21.11";

        [ObservableProperty]
        private int _modpackRamMB = 4096;

        public ObservableCollection<string> ModpackLoaders { get; } = new() { "Fabric", "NeoForge", "Forge", "Quilt", "Vanilla" };
        public ObservableCollection<string> ModpackVersions { get; } = new()
        {
            "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.1", "1.21",
            "1.20.6", "1.20.4", "1.20.2", "1.20.1",
            "1.19.4", "1.19.2", "1.18.2", "1.16.5",
            "1.12.2", "1.8.9", "1.7.10"
        };

        // ─── 2. TARGET INSTANCE SELECTOR MODAL STATE (Mods, Shaders, Resource Packs, Data Packs) ───
        [ObservableProperty]
        private bool _isTargetInstanceModalOpen;

        [ObservableProperty]
        private ModpackInfo? _pendingContentProject;

        [ObservableProperty]
        private string _targetContentType = "Mod";

        [ObservableProperty]
        private MinecraftInstance? _selectedTargetInstance;

        // ─── Edit Instance Modal State ───
        [ObservableProperty]
        private bool _isEditModalOpen;

        [ObservableProperty]
        private MinecraftInstance? _editingInstance;

        [ObservableProperty]
        private string _editName = string.Empty;

        [ObservableProperty]
        private string _editVersion = "1.21.11";

        [ObservableProperty]
        private string _editLoader = "Fabric";

        [ObservableProperty]
        private int _editRamMB = 4096;

        [ObservableProperty]
        private string _editJvmArguments = string.Empty;

        private readonly IVersionService? _versionService;

        public InstallationManagerViewModel(MainViewModel main)
        {
            _main = main;
            try
            {
                _instanceService = ServiceLocator.Resolve<IInstanceService>();
                _versionService = ServiceLocator.Resolve<IVersionService>();
                LoadInstallations();

                _instanceService.InstancesChanged += () =>
                {
                    var app = Application.Current;
                    if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess())
                    {
                        app.Dispatcher.BeginInvoke(LoadInstallations);
                    }
                    else
                    {
                        LoadInstallations();
                    }
                };
            }
            catch { }
        }

        public Task InitializeAsync()
        {
            LoadInstallations();
            return Task.CompletedTask;
        }

        public void Activate()
        {
            LoadInstallations();
            if (ActiveTab != "Installations")
            {
                _ = LoadModrinthProjectsAsync();
            }
        }

        public void Deactivate()
        {
            IsModpackModalOpen = false;
            IsTargetInstanceModalOpen = false;
            IsEditModalOpen = false;
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
        }

        partial void OnActiveTabChanged(string value)
        {
            CurrentPage = 1;
            if (value != "Installations")
            {
                _ = LoadModrinthProjectsAsync();
            }
        }

        partial void OnLibraryFilterLoaderChanged(string value) => ApplyLibraryFilterAndSort();
        partial void OnLibrarySortByChanged(string value) => ApplyLibraryFilterAndSort();
        partial void OnLibrarySearchQueryChanged(string value) => ApplyLibraryFilterAndSort();

        public void LoadInstallations()
        {
            Installations.Clear();
            if (_instanceService == null) return;
            foreach (var inst in _instanceService.GetAllInstances())
            {
                Installations.Add(inst);
            }
            HasInstallations = Installations.Count > 0;
            SelectedInstallation = _instanceService.GetActiveInstance();
            if (SelectedTargetInstance == null && Installations.Count > 0)
            {
                SelectedTargetInstance = Installations.FirstOrDefault(i => i.IsActive) ?? Installations.FirstOrDefault();
            }
            ApplyLibraryFilterAndSort();
        }

        public void ApplyLibraryFilterAndSort()
        {
            FilteredInstallations.Clear();
            IEnumerable<MinecraftInstance> query = Installations;

            // 1. Filter by Loader / Favorites
            if (!string.IsNullOrEmpty(LibraryFilterLoader) && LibraryFilterLoader != "All")
            {
                if (LibraryFilterLoader == "Favorites")
                {
                    query = query.Where(i => i.IsFavorite);
                }
                else
                {
                    query = query.Where(i => string.Equals(i.Loader, LibraryFilterLoader, StringComparison.OrdinalIgnoreCase));
                }
            }

            // 2. Filter by Search Query
            if (!string.IsNullOrWhiteSpace(LibrarySearchQuery))
            {
                var q = LibrarySearchQuery.Trim().ToLowerInvariant();
                query = query.Where(i => (i.Name ?? "").ToLowerInvariant().Contains(q) 
                                      || (i.MinecraftVersion ?? "").ToLowerInvariant().Contains(q)
                                      || (i.Loader ?? "").ToLowerInvariant().Contains(q));
            }

            // 3. Sort
            query = LibrarySortBy switch
            {
                "Name" => query.OrderBy(i => i.Name),
                "Version" => query.OrderByDescending(i => i.MinecraftVersion),
                "RAM" => query.OrderByDescending(i => i.RamMB),
                _ => query.OrderByDescending(i => i.IsActive)
                          .ThenByDescending(i => i.IsFavorite)
                          .ThenByDescending(i => i.LastPlayedAt ?? DateTime.MinValue)
                          .ThenBy(i => i.Name)
            };

            foreach (var inst in query)
            {
                FilteredInstallations.Add(inst);
            }
        }

        [RelayCommand]
        private void SelectTab(object? tab)
        {
            if (tab is string s)
            {
                ActiveTab = s;
            }
        }

        [RelayCommand]
        private void SelectInstallation(MinecraftInstance? instance)
        {
            if (instance == null || _instanceService == null) return;
            _instanceService.SetActiveInstance(instance.InstanceId);
            SelectedInstallation = instance;
            SelectedTargetInstance = instance;
            LoadInstallations();
            _main.ShowNotification("Active Instance Updated", $"Switched to {instance.Name}", NotificationType.Success);
        }

        [RelayCommand]
        private void ToggleFavorite(MinecraftInstance? instance)
        {
            if (instance == null || _instanceService == null) return;
            instance.IsFavorite = !instance.IsFavorite;
            _ = _instanceService.SaveInstanceAsync(instance);
            ApplyLibraryFilterAndSort();
        }

        [RelayCommand]
        private void InspectInstance(MinecraftInstance? instance)
        {
            if (instance == null) return;
            InspectingInstance = instance;
            IsInspectOpen = true;
        }

        [RelayCommand]
        private void CloseInspect()
        {
            IsInspectOpen = false;
            InspectingInstance = null;
        }

        [RelayCommand]
        private void PlayInstanceDirectly(MinecraftInstance? instance)
        {
            if (instance == null || _instanceService == null) return;
            _instanceService.SetActiveInstance(instance.InstanceId);
            SelectedInstallation = instance;
            _main.NavigateTo("Home");
            if (_main.CurrentPageViewModel is HomeViewModel homeVm)
            {
                homeVm.PlayCommand.Execute(null);
            }
        }

        [RelayCommand]
        private void OpenInstanceFolder(MinecraftInstance? instance)
        {
            var inst = instance ?? SelectedInstallation;
            if (inst == null) return;
            try
            {
                var dir = !string.IsNullOrEmpty(inst.GameDirectory) && Directory.Exists(inst.GameDirectory)
                    ? inst.GameDirectory
                    : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Instances", inst.Name);

                Directory.CreateDirectory(dir);
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("explorer.exe", dir) { UseShellExecute = true });
                CrashLogger.LogMessage($"[Instance]: Opened folder for '{inst.Name}' in File Explorer: {dir}");
                _main.ShowNotification("Folder Opened", $"Opened folder for '{inst.Name}' in Explorer.", NotificationType.Info);
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Error", $"Could not open folder: {ex.Message}", NotificationType.Error);
            }
        }

        [RelayCommand]
        private void OpenGameDirectory()
        {
            var inst = SelectedInstallation ?? _instanceService?.GetActiveInstance();
            if (inst != null)
            {
                OpenInstanceFolder(inst);
                return;
            }

            try
            {
                var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Instances");
                Directory.CreateDirectory(dir);
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo("explorer.exe", dir) { UseShellExecute = true });
                _main.ShowNotification("Folder Opened", "Opened Instances folder in Explorer.", NotificationType.Info);
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Error", $"Could not open folder: {ex.Message}", NotificationType.Error);
            }
        }

        [RelayCommand]
        private void DeleteInstance(MinecraftInstance? instance)
        {
            var inst = instance ?? SelectedInstallation;
            if (inst == null || _instanceService == null) return;

            var name = inst.Name;
            _instanceService.DeleteInstance(inst.InstanceId);
            LoadInstallations();
            CrashLogger.LogMessage($"[Instance]: Deleted instance '{name}' ({inst.InstanceId})");
            _main.ShowNotification("Instance Deleted", $"Instance '{name}' has been deleted.", NotificationType.Info);
        }

        [RelayCommand]
        public void EditInstance(MinecraftInstance? instance)
        {
            var inst = instance ?? SelectedInstallation;
            if (inst == null) return;

            EditingInstance = inst;
            EditName = inst.Name;
            EditVersion = inst.MinecraftVersion;
            EditLoader = inst.Loader;
            EditRamMB = inst.RamMB;
            EditJvmArguments = inst.JvmArguments ?? string.Empty;
            IsEditModalOpen = true;
        }

        [RelayCommand]
        private async Task SaveInstanceEdit()
        {
            if (EditingInstance == null || _instanceService == null)
            {
                IsEditModalOpen = false;
                return;
            }

            if (!string.IsNullOrWhiteSpace(EditName))
            {
                EditingInstance.Name = EditName.Trim();
            }
            EditingInstance.MinecraftVersion = EditVersion;
            EditingInstance.Loader = EditLoader;
            EditingInstance.RamMB = EditRamMB;
            EditingInstance.JvmArguments = EditJvmArguments;

            await _instanceService.SaveInstanceAsync(EditingInstance);
            LoadInstallations();
            CrashLogger.LogMessage($"[Instance]: Saved changes to instance '{EditingInstance.Name}' ({EditingInstance.Loader} {EditingInstance.MinecraftVersion}, {EditingInstance.RamMB}MB)");
            _main.ShowNotification("Instance Updated", $"Successfully saved changes for '{EditingInstance.Name}'!", NotificationType.Success);
            IsEditModalOpen = false;
        }

        [RelayCommand]
        private void CancelEdit()
        {
            IsEditModalOpen = false;
            EditingInstance = null;
        }

        [RelayCommand]
        private void NewInstallation()
        {
            _main.NavigateTo("Versions"); // Opens the instance creation wizard
        }

        [RelayCommand]
        private async Task RepairInstallation(MinecraftInstance? instance)
        {
            var target = instance ?? SelectedInstallation;
            if (target == null) return;

            _main.ShowNotification("Repairing Instance", $"Validating and repairing '{target.Name}' (Minecraft {target.MinecraftVersion})...", NotificationType.Info);

            try
            {
                var integrityService = ServiceLocator.Resolve<Services.Integrity.IInstanceIntegrityService>();
                if (integrityService != null)
                {
                    var progress = new Progress<DownloadProgressInfo>(p =>
                    {
                        CrashLogger.LogMessage($"[Repair]: {p.CurrentOperation} ({p.CompletedFiles}/{p.TotalFiles})");
                    });

                    bool ok = await Task.Run(() => integrityService.RepairInstanceAsync(target, progress));
                    if (ok)
                    {
                        LoadInstallations();
                        _main.ShowNotification("Repair Complete", $"Instance '{target.Name}' ({target.Loader} {target.MinecraftVersion}) was successfully verified and repaired!", NotificationType.Success);
                    }
                    else
                    {
                        _main.ShowNotification("Repair Incomplete", $"Could not complete all repair steps for '{target.Name}'. Check logs for details.", NotificationType.Warning);
                    }
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("RepairInstallation", ex);
                _main.ShowNotification("Repair Failed", $"Repair error: {ex.Message}", NotificationType.Error);
            }
        }

        [RelayCommand]
        private async Task ImportModpack()
        {
            try
            {
                var dialog = new Microsoft.Win32.OpenFileDialog
                {
                    Title = "Import Minecraft Instance / Modpack (.mrpack, .zip)",
                    Filter = "Minecraft Packages (*.mrpack;*.zip)|*.mrpack;*.zip|Modrinth Modpack (*.mrpack)|*.mrpack|Zip Archive (*.zip)|*.zip|All Files (*.*)|*.*",
                    Multiselect = false
                };

                if (dialog.ShowDialog() != true) return;

                string filePath = dialog.FileName;
                string fileName = Path.GetFileNameWithoutExtension(filePath);

                string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                string instanceName = fileName;
                string instanceDir = Path.Combine(appData, "VayuClient", "Instances", instanceName);

                int counter = 1;
                while (Directory.Exists(instanceDir))
                {
                    instanceName = $"{fileName}_{counter++}";
                    instanceDir = Path.Combine(appData, "VayuClient", "Instances", instanceName);
                }

                Directory.CreateDirectory(instanceDir);

                var instance = new MinecraftInstance
                {
                    InstanceId = Guid.NewGuid().ToString("N"),
                    Name = instanceName,
                    MinecraftVersion = "1.21.11",
                    Loader = "Fabric",
                    GameDirectory = instanceDir,
                    RamMB = 4096,
                    CreatedAt = DateTime.UtcNow,
                    LastPlayedAt = DateTime.UtcNow,
                    IsActive = true
                };

                if (_instanceService != null)
                {
                    await _instanceService.CreateInstanceAsync(instance);
                    _instanceService.SetActiveInstance(instance.InstanceId);
                }

                _main.ShowNotification("Importing Modpack", $"Analyzing and extracting package '{fileName}'...", NotificationType.Info);

                var modpackInstaller = ServiceLocator.Resolve<IModpackInstaller>();
                if (modpackInstaller != null)
                {
                    var progress = new Progress<DownloadProgressInfo>(p =>
                    {
                        CrashLogger.LogMessage($"[Import]: {p.CurrentOperation} ({p.CompletedFiles}/{p.TotalFiles})");
                    });

                    await Task.Run(() => modpackInstaller.InstallLocalArchiveAsync(instance, filePath, progress));
                }

                if (_instanceService != null)
                {
                    await _instanceService.SaveInstanceAsync(instance);
                    _instanceService.SetActiveInstance(instance.InstanceId);
                }

                LoadInstallations();
                _main.ShowNotification("Instance Imported", $"Successfully imported '{instance.Name}' ({instance.Loader} {instance.MinecraftVersion}) with all mods and settings!", NotificationType.Success);
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ImportModpack", ex);
                _main.ShowNotification("Import Failed", $"Could not import package: {ex.Message}", NotificationType.Error);
            }
        }

        [RelayCommand]
        private async Task SearchModrinth()
        {
            CurrentPage = 1;
            await LoadModrinthProjectsAsync();
        }

        [RelayCommand]
        private async Task NextPage()
        {
            if (CurrentPage < TotalPages)
            {
                CurrentPage++;
                await LoadModrinthProjectsAsync();
            }
        }

        [RelayCommand]
        private async Task PreviousPage()
        {
            if (CurrentPage > 1)
            {
                CurrentPage--;
                await LoadModrinthProjectsAsync();
            }
        }

        [RelayCommand]
        private async Task GoToPage(object? pageObj)
        {
            if (pageObj is int page && page >= 1 && page <= TotalPages && page != CurrentPage)
            {
                CurrentPage = page;
                await LoadModrinthProjectsAsync();
            }
        }

        [RelayCommand]
        private void OpenModpackModal(ModpackInfo? project)
        {
            InstallProject(project);
        }

        [RelayCommand]
        private void InstallProject(ModpackInfo? project)
        {
            if (project == null) return;

            if (project.ProjectType == "modpack" || ActiveTab == "Modpacks")
            {
                PendingModpack = project;
                ModpackInstanceName = string.IsNullOrWhiteSpace(project.Title) ? "Modpack Instance" : project.Title;
                ModpackRamMB = 4096;

                // 1. Initial populate from project's search metadata
                ModpackLoaders.Clear();
                if (project.CompatibleLoaders != null && project.CompatibleLoaders.Count > 0)
                {
                    foreach (var l in project.CompatibleLoaders) ModpackLoaders.Add(l);
                }
                else
                {
                    ModpackLoaders.Add("Fabric");
                    ModpackLoaders.Add("NeoForge");
                    ModpackLoaders.Add("Forge");
                    ModpackLoaders.Add("Quilt");
                    ModpackLoaders.Add("Vanilla");
                }

                ModpackVersions.Clear();
                if (project.CompatibleVersions != null && project.CompatibleVersions.Count > 0)
                {
                    foreach (var v in project.CompatibleVersions) ModpackVersions.Add(v);
                }
                else
                {
                    var defaults = new[] { "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.1", "1.21", "1.20.6", "1.20.4", "1.20.2", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.16.5", "1.12.2", "1.8.9", "1.7.10" };
                    foreach (var d in defaults) ModpackVersions.Add(d);
                }

                SelectedModpackLoader = ModpackLoaders.FirstOrDefault() ?? "Fabric";
                SelectedModpackVersion = ModpackVersions.FirstOrDefault() ?? "1.21.11";
                IsModpackModalOpen = true;

                // 2. Query real-time versions from Modrinth API for this specific project
                _ = Task.Run(async () =>
                {
                    try
                    {
                        string versionsUrl = $"https://api.modrinth.com/v2/project/{Uri.EscapeDataString(project.Id)}/version";
                        using var res = await _httpClient.GetAsync(versionsUrl);
                        if (res.IsSuccessStatusCode)
                        {
                            var json = await res.Content.ReadAsStringAsync();
                            using var doc = JsonDocument.Parse(json);
                            if (doc.RootElement.ValueKind == JsonValueKind.Array)
                            {
                                var distinctGameVersions = new HashSet<string>();
                                var distinctLoaders = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

                                foreach (var ver in doc.RootElement.EnumerateArray())
                                {
                                    if (ver.TryGetProperty("game_versions", out var gvArray) && gvArray.ValueKind == JsonValueKind.Array)
                                    {
                                        foreach (var gv in gvArray.EnumerateArray())
                                        {
                                            if (gv.GetString() is string gvStr && !string.IsNullOrWhiteSpace(gvStr))
                                            {
                                                distinctGameVersions.Add(gvStr);
                                            }
                                        }
                                    }

                                    if (ver.TryGetProperty("loaders", out var lArray) && lArray.ValueKind == JsonValueKind.Array)
                                    {
                                        foreach (var l in lArray.EnumerateArray())
                                        {
                                            if (l.GetString() is string lStr && !string.IsNullOrWhiteSpace(lStr))
                                            {
                                                string norm = char.ToUpper(lStr[0]) + lStr.Substring(1).ToLowerInvariant();
                                                if (norm.Equals("Neoforge", StringComparison.OrdinalIgnoreCase)) norm = "NeoForge";
                                                distinctLoaders.Add(norm);
                                            }
                                        }
                                    }
                                }

                                if (distinctGameVersions.Count > 0)
                                {
                                    var sortedVersions = distinctGameVersions.ToList();
                                    sortedVersions.Sort((a, b) => MinecraftVersionComparer.Instance.Compare(
                                        new MinecraftVersion { Id = b },
                                        new MinecraftVersion { Id = a }));

                                    var app = Application.Current;
                                    void ApplyVersions()
                                    {
                                        if (PendingModpack?.Id == project.Id)
                                        {
                                            ModpackVersions.Clear();
                                            foreach (var v in sortedVersions) ModpackVersions.Add(v);

                                            if (string.IsNullOrEmpty(SelectedModpackVersion) || !ModpackVersions.Contains(SelectedModpackVersion))
                                            {
                                                SelectedModpackVersion = ModpackVersions.FirstOrDefault() ?? "1.21.11";
                                            }

                                            if (distinctLoaders.Count > 0)
                                            {
                                                ModpackLoaders.Clear();
                                                foreach (var l in distinctLoaders) ModpackLoaders.Add(l);

                                                if (string.IsNullOrEmpty(SelectedModpackLoader) || !ModpackLoaders.Contains(SelectedModpackLoader))
                                                {
                                                    SelectedModpackLoader = ModpackLoaders.FirstOrDefault() ?? "Fabric";
                                                }
                                            }
                                        }
                                    }

                                    if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess() && !app.Dispatcher.HasShutdownStarted)
                                    {
                                        try { app.Dispatcher.Invoke(ApplyVersions); } catch { ApplyVersions(); }
                                    }
                                    else
                                    {
                                        ApplyVersions();
                                    }
                                }
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        CrashLogger.LogException("FetchModpackProjectVersions", ex);
                    }
                });
            }
            else
            {
                var instances = _instanceService?.GetAllInstances() ?? Array.Empty<MinecraftInstance>();
                if (instances.Count == 0)
                {
                    _main.ShowNotification("No Instances Found", "Please create an instance first before installing mods or packs.", NotificationType.Warning);
                    return;
                }

                PendingContentProject = project;
                TargetContentType = ActiveTab switch
                {
                    "Mods" => "Mod",
                    "Resource Packs" => "Resource Pack",
                    "Shaders" => "Shader",
                    "Data Packs" => "Data Pack",
                    _ => "Mod"
                };
                SelectedTargetInstance = SelectedInstallation ?? instances.FirstOrDefault(i => i.IsActive) ?? instances.FirstOrDefault();
                IsTargetInstanceModalOpen = true;
            }
        }

        [RelayCommand]
        private async Task ConfirmModpackInstall()
        {
            if (PendingModpack == null || _instanceService == null) return;

            var modpack = PendingModpack;
            IsModpackModalOpen = false;

            try
            {
                var name = string.IsNullOrWhiteSpace(ModpackInstanceName) ? modpack.Title : ModpackInstanceName.Trim();
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var instDir = Path.Combine(appData, "VayuClient", "Instances", name);
                Directory.CreateDirectory(instDir);

                var instance = new MinecraftInstance
                {
                    InstanceId = Guid.NewGuid().ToString("N"),
                    Name = name,
                    MinecraftVersion = SelectedModpackVersion,
                    Loader = SelectedModpackLoader,
                    ModpackId = modpack.Id,
                    GameDirectory = instDir,
                    RamMB = ModpackRamMB,
                    CreatedAt = DateTime.UtcNow,
                    LastPlayedAt = DateTime.UtcNow,
                    IsActive = true
                };

                await _instanceService.CreateInstanceAsync(instance);
                _instanceService.SetActiveInstance(instance.InstanceId);
                LoadInstallations();

                CrashLogger.LogMessage($"[Instance]: Created new modpack instance '{instance.Name}' ({instance.Loader} {instance.MinecraftVersion}) from '{modpack.Title}'");
                _main.ShowNotification("Downloading Modpack", $"Downloading mods and resources for {modpack.Title}...", NotificationType.Info);

                // Download all modpack mods & extract overrides immediately
                var modpackInstaller = ServiceLocator.Resolve<IModpackInstaller>();
                if (modpackInstaller != null)
                {
                    var progress = new Progress<DownloadProgressInfo>(p =>
                    {
                        CrashLogger.LogMessage($"[Modpack Install]: {p.CurrentOperation} ({p.CompletedFiles}/{p.TotalFiles})");
                    });

                    bool ok = await Task.Run(() => modpackInstaller.InstallModpackAsync(instance, instance.ModpackId, progress));
                    if (ok)
                    {
                        _main.ShowNotification("Modpack Ready", $"Successfully installed all mods and resources for {instance.Name}!", NotificationType.Success);
                    }
                    else
                    {
                        _main.ShowNotification("Modpack Created", $"Created '{instance.Name}'. Remaining mods will be verified on launch.", NotificationType.Info);
                    }
                }
                else
                {
                    _main.ShowNotification("Modpack Ready", $"Created '{instance.Name}'!", NotificationType.Success);
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ConfirmModpackInstall", ex);
                _main.ShowNotification("Error", $"Could not install modpack: {ex.Message}", NotificationType.Error);
            }
        }

        [RelayCommand]
        private void CancelModpackInstall()
        {
            IsModpackModalOpen = false;
            PendingModpack = null;
        }

        [RelayCommand]
        private async Task ConfirmTargetInstance()
        {
            if (PendingContentProject == null || SelectedTargetInstance == null) return;

            var project = PendingContentProject;
            var instance = SelectedTargetInstance;
            var contentType = TargetContentType;
            IsTargetInstanceModalOpen = false;

            try
            {
                string targetSubDir = contentType switch
                {
                    "Mod" => "mods",
                    "Resource Pack" => "resourcepacks",
                    "Shader" => "shaderpacks",
                    "Data Pack" => "datapacks",
                    _ => "mods"
                };

                string fullTargetDir = Path.Combine(instance.GameDirectory, targetSubDir);
                Directory.CreateDirectory(fullTargetDir);

                _main.ShowNotification("Downloading", $"Fetching {contentType} file for {project.Title}...", NotificationType.Info);

                // 1. Query Modrinth for project version
                string projectId = project.Id;
                string versionsUrl = $"https://api.modrinth.com/v2/project/{Uri.EscapeDataString(projectId)}/version";

                var response = await _httpClient.GetAsync(versionsUrl);
                if (!response.IsSuccessStatusCode)
                {
                    _main.ShowNotification("Download Failed", $"Could not find versions for {project.Title}.", NotificationType.Error);
                    return;
                }

                var json = await response.Content.ReadAsStringAsync();
                var versions = JsonDocument.Parse(json).RootElement;
                if (versions.ValueKind != JsonValueKind.Array || versions.GetArrayLength() == 0)
                {
                    _main.ShowNotification("Download Failed", $"No downloadable files found for {project.Title}.", NotificationType.Error);
                    return;
                }

                // 2. Find compatible version for instance (MinecraftVersion + Loader)
                JsonElement selectedVer = default;
                bool found = false;

                foreach (var ver in versions.EnumerateArray())
                {
                    bool verMatch = true;
                    if (ver.TryGetProperty("game_versions", out var gvArr) && gvArr.ValueKind == JsonValueKind.Array)
                    {
                        var gvs = gvArr.EnumerateArray().Select(v => v.GetString() ?? "").ToList();
                        verMatch = gvs.Count == 0 || gvs.Contains(instance.MinecraftVersion);
                    }

                    bool loaderMatch = true;
                    if (contentType == "Mod" && ver.TryGetProperty("loaders", out var loadArr) && loadArr.ValueKind == JsonValueKind.Array)
                    {
                        var lds = loadArr.EnumerateArray().Select(l => (l.GetString() ?? "").ToLowerInvariant()).ToList();
                        loaderMatch = lds.Count == 0 || string.IsNullOrEmpty(instance.Loader) || lds.Contains(instance.Loader.ToLowerInvariant());
                    }

                    if (verMatch && loaderMatch)
                    {
                        selectedVer = ver;
                        found = true;
                        break;
                    }
                }

                if (!found)
                {
                    selectedVer = versions.EnumerateArray().First();
                }

                // 3. Extract primary file
                if (!selectedVer.TryGetProperty("files", out var filesArr) || filesArr.ValueKind != JsonValueKind.Array || filesArr.GetArrayLength() == 0)
                {
                    _main.ShowNotification("Download Failed", $"No downloadable files found for {project.Title}.", NotificationType.Error);
                    return;
                }

                JsonElement primaryFile = default;
                bool hasPrimary = false;
                foreach (var f in filesArr.EnumerateArray())
                {
                    if (f.TryGetProperty("primary", out var prim) && prim.GetBoolean())
                    {
                        primaryFile = f;
                        hasPrimary = true;
                        break;
                    }
                }
                if (!hasPrimary) primaryFile = filesArr.EnumerateArray().First();

                string fileUrl = primaryFile.GetProperty("url").GetString() ?? "";
                string fileName = primaryFile.GetProperty("filename").GetString() ?? $"{project.Title}.jar";

                if (string.IsNullOrEmpty(fileUrl))
                {
                    _main.ShowNotification("Download Failed", $"Invalid download URL for {project.Title}.", NotificationType.Error);
                    return;
                }

                string destPath = Path.Combine(fullTargetDir, fileName);

                // 4. Download file to instance folder
                using var fileRes = await _httpClient.GetAsync(fileUrl);
                if (!fileRes.IsSuccessStatusCode)
                {
                    _main.ShowNotification("Download Failed", $"Server returned {fileRes.StatusCode} downloading {fileName}", NotificationType.Error);
                    return;
                }

                await using (var fs = new FileStream(destPath, FileMode.Create, FileAccess.Write, FileShare.None))
                {
                    await fileRes.Content.CopyToAsync(fs);
                }

                CrashLogger.LogMessage($"[Content]: Downloaded {fileName} ({contentType}) to {destPath}");
                _main.ShowNotification("Installed Successfully", $"Installed {fileName} into {instance.Name} ({contentType})!", NotificationType.Success);
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("InstallContent", ex);
                _main.ShowNotification("Install Error", $"Failed to install {project.Title}: {ex.Message}", NotificationType.Error);
            }
        }

        [RelayCommand]
        private async Task ConfirmContentInstall()
        {
            await ConfirmTargetInstance();
        }

        [RelayCommand]
        private void CancelContentInstall()
        {
            CancelTargetInstance();
        }

        [RelayCommand]
        private void CancelTargetInstance()
        {
            IsTargetInstanceModalOpen = false;
            PendingContentProject = null;
        }

        public async Task LoadModrinthProjectsAsync()
        {
            IsLoadingModrinth = true;
            StatusMessage = "Connecting to Modrinth...";

            string projectType = ActiveTab switch
            {
                "Modpacks" => "modpack",
                "Mods" => "mod",
                "Resource Packs" => "resourcepack",
                "Shaders" => "shader",
                "Data Packs" => "datapack",
                "Maps" => "modpack",
                _ => "modpack"
            };

            var results = new List<ModpackInfo>();
            int totalHits = 0;

            try
            {
                string encodedQuery = Uri.EscapeDataString(SearchQuery.Trim());
                int offset = (CurrentPage - 1) * PageSize;
                string url = $"https://api.modrinth.com/v2/search?query={encodedQuery}&facets=[[\"project_type:{projectType}\"]]&limit={PageSize}&offset={offset}";

                using var response = await _httpClient.GetAsync(url);
                if (response.IsSuccessStatusCode)
                {
                    var jsonStr = await response.Content.ReadAsStringAsync();
                    using var doc = JsonDocument.Parse(jsonStr);

                    if (doc.RootElement.TryGetProperty("total_hits", out var thProp))
                    {
                        totalHits = thProp.GetInt32();
                    }

                    if (doc.RootElement.TryGetProperty("hits", out var hits))
                    {
                        foreach (var hit in hits.EnumerateArray())
                        {
                            string id = hit.TryGetProperty("project_id", out var idProp) ? idProp.GetString() ?? "" : "";
                            string title = hit.TryGetProperty("title", out var titleProp) ? titleProp.GetString() ?? "" : "";
                            string desc = hit.TryGetProperty("description", out var descProp) ? descProp.GetString() ?? "" : "";
                            string author = hit.TryGetProperty("author", out var authorProp) ? authorProp.GetString() ?? "" : "";
                            string iconUrl = hit.TryGetProperty("icon_url", out var iconProp) ? iconProp.GetString() ?? "" : "";
                            int downloads = hit.TryGetProperty("downloads", out var downProp) ? downProp.GetInt32() : 0;

                            string downStr = downloads >= 1_000_000 ? $"{(downloads / 1_000_000.0):0.#}M downloads" :
                                            downloads >= 1_000 ? $"{(downloads / 1_000.0):0.#}K downloads" : $"{downloads} downloads";

                            var compVersions = new List<string>();
                            if (hit.TryGetProperty("versions", out var vArray) && vArray.ValueKind == JsonValueKind.Array)
                            {
                                foreach (var v in vArray.EnumerateArray())
                                    if (v.GetString() is string vStr && !string.IsNullOrWhiteSpace(vStr))
                                        compVersions.Add(vStr);
                            }

                            var compLoaders = new List<string>();
                            if (hit.TryGetProperty("categories", out var cArray) && cArray.ValueKind == JsonValueKind.Array)
                            {
                                foreach (var c in cArray.EnumerateArray())
                                {
                                    if (c.GetString() is string cStr && !string.IsNullOrWhiteSpace(cStr))
                                    {
                                        string norm = char.ToUpper(cStr[0]) + cStr.Substring(1).ToLowerInvariant();
                                        if (norm.Equals("Neoforge", StringComparison.OrdinalIgnoreCase)) norm = "NeoForge";
                                        compLoaders.Add(norm);
                                    }
                                }
                            }

                            results.Add(new ModpackInfo
                            {
                                Id = id,
                                Title = title,
                                Description = desc,
                                Author = author,
                                IconUrl = iconUrl,
                                ProjectType = projectType,
                                DownloadsDisplay = downStr,
                                CompatibleVersions = compVersions,
                                CompatibleLoaders = compLoaders,
                                IsCompatible = true
                            });

                            // Preload images
                            if (!string.IsNullOrEmpty(iconUrl))
                            {
                                _ = Task.Run(async () =>
                                {
                                    try
                                    {
                                        var bytes = await _httpClient.GetByteArrayAsync(iconUrl);
                                        // Cache strategy could be implemented here
                                    }
                                    catch { }
                                });
                            }
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Modrinth API load error: {ex.Message}");
                StatusMessage = "Could not connect to Modrinth. Please check internet connection.";
            }

            var app = Application.Current;
            void ApplyState()
            {
                ModrinthProjects.Clear();
                foreach (var item in results)
                {
                    ModrinthProjects.Add(item);
                }

                TotalHits = totalHits > 0 ? totalHits : ModrinthProjects.Count;
                TotalPages = Math.Max(1, (int)Math.Ceiling(TotalHits / (double)PageSize));
                CanGoPrevious = CurrentPage > 1;
                CanGoNext = CurrentPage < TotalPages;

                PageNumbers.Clear();
                int startPage = Math.Max(1, CurrentPage - 2);
                int endPage = Math.Min(TotalPages, startPage + 4);
                if (endPage - startPage < 4)
                {
                    startPage = Math.Max(1, endPage - 4);
                }
                for (int p = startPage; p <= endPage; p++)
                {
                    PageNumbers.Add(p);
                }

                int startItem = TotalHits == 0 ? 0 : (CurrentPage - 1) * PageSize + 1;
                int endItem = Math.Min(CurrentPage * PageSize, TotalHits);
                PaginationSummary = $"Showing {startItem}–{endItem} of {TotalHits} projects";

                IsLoadingModrinth = false;
                StatusMessage = ModrinthProjects.Count > 0 
                    ? $"{PaginationSummary} on Modrinth" 
                    : "No projects found on Modrinth for this category.";
            }

            if (app?.Dispatcher != null && !app.Dispatcher.CheckAccess() && !app.Dispatcher.HasShutdownStarted)
            {
                try { app.Dispatcher.Invoke(ApplyState); } catch { ApplyState(); }
            }
            else
            {
                ApplyState();
            }
        }

        [RelayCommand]
        private void Close()
        {
            _main.NavigateTo("Home");
        }
    }
}
