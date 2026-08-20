using System;
using System.Collections.ObjectModel;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Win32;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Instance;

namespace VayuClient.ViewModels
{
    public partial class ModsViewModel : ObservableObject, ILifecycleViewModel
    {
        private readonly MainViewModel _main;
        private readonly IInstanceService _instanceService;
        private bool _disposed;

        [ObservableProperty]
        private string _searchQuery = string.Empty;

        [ObservableProperty]
        private string _selectedCategory = "ALL";

        [ObservableProperty]
        private string _statusFilter = "All"; // All, Enabled, Disabled

        public ObservableCollection<string> Categories { get; } = new() 
        { 
            "ALL", "PERFORMANCE", "PVP", "HUD", "UTILITY", "VISUAL", "WORLD", "LIBRARY" 
        };

        public ObservableCollection<string> StatusFilters { get; } = new() { "All", "Enabled", "Disabled" };

        [ObservableProperty]
        private string _activeInstanceName = "No Instance Selected";

        [ObservableProperty]
        private bool _hasMods = false;

        [ObservableProperty]
        private int _totalModCount = 0;

        [ObservableProperty]
        private int _enabledModCount = 0;

        [ObservableProperty]
        private MinecraftInstance? _selectedInstance;

        [ObservableProperty]
        private bool _hasSelectedInstance;

        [ObservableProperty]
        private bool _hasInstances;

        public ObservableCollection<MinecraftInstance> AvailableInstances { get; } = new();
        public ObservableCollection<ModInfo> Mods { get; } = new();
        private readonly List<ModInfo> _allMods = new();

        public ModsViewModel(MainViewModel main)
        {
            _main = main;
            _instanceService = ServiceLocator.Resolve<IInstanceService>();
            LoadInstances();
        }

        public Task InitializeAsync()
        {
            LoadInstances();
            return Task.CompletedTask;
        }

        public void Activate()
        {
            LoadInstances();
        }

        public void Deactivate()
        {
        }

        public void Dispose()
        {
            if (_disposed) return;
            _disposed = true;
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

        public void LoadInstances()
        {
            try
            {
                var instances = _instanceService.GetAllInstances();
                Dispatch(() =>
                {
                    AvailableInstances.Clear();
                    foreach (var inst in instances)
                    {
                        AvailableInstances.Add(inst);
                    }
                    HasInstances = AvailableInstances.Count > 0;

                    var active = _instanceService.GetActiveInstance();
                    if (active != null)
                    {
                        SelectedInstance = AvailableInstances.FirstOrDefault(i => i.InstanceId == active.InstanceId) 
                                           ?? AvailableInstances.FirstOrDefault();
                    }
                    else
                    {
                        SelectedInstance = AvailableInstances.FirstOrDefault();
                    }

                    HasSelectedInstance = SelectedInstance != null;
                    if (SelectedInstance != null)
                    {
                        RefreshModsForInstance(SelectedInstance);
                    }
                    else
                    {
                        Mods.Clear();
                        HasMods = false;
                        ActiveInstanceName = "No Instance Selected";
                    }
                });
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("LoadInstancesInMods", ex);
            }
        }

        partial void OnSelectedInstanceChanged(MinecraftInstance? value)
        {
            HasSelectedInstance = value != null;
            if (value != null)
            {
                _instanceService.SetActiveInstance(value.InstanceId);
                RefreshModsForInstance(value);
            }
            else
            {
                Mods.Clear();
                HasMods = false;
                ActiveInstanceName = "No Instance Selected";
            }
        }

        partial void OnSearchQueryChanged(string value) => ApplyFilter();
        partial void OnSelectedCategoryChanged(string value) => ApplyFilter();
        partial void OnStatusFilterChanged(string value) => ApplyFilter();

        private void ApplyFilter()
        {
            Dispatch(() =>
            {
                Mods.Clear();
                IEnumerable<ModInfo> query = _allMods;

                // 1. Filter Category
                if (!string.IsNullOrEmpty(SelectedCategory) && SelectedCategory != "ALL")
                {
                    query = query.Where(m => string.Equals(m.Category, SelectedCategory, StringComparison.OrdinalIgnoreCase));
                }

                // 2. Filter Status
                if (StatusFilter == "Enabled")
                {
                    query = query.Where(m => m.IsEnabled);
                }
                else if (StatusFilter == "Disabled")
                {
                    query = query.Where(m => !m.IsEnabled);
                }

                // 3. Filter Search Query
                if (!string.IsNullOrWhiteSpace(SearchQuery))
                {
                    var q = SearchQuery.Trim().ToLowerInvariant();
                    query = query.Where(m => (m.Name ?? "").ToLowerInvariant().Contains(q) ||
                                             (m.Description ?? "").ToLowerInvariant().Contains(q) ||
                                             (m.Author ?? "").ToLowerInvariant().Contains(q) ||
                                             (m.Id ?? "").ToLowerInvariant().Contains(q));
                }

                foreach (var m in query) Mods.Add(m);
                HasMods = Mods.Count > 0;
                TotalModCount = _allMods.Count;
                EnabledModCount = _allMods.Count(m => m.IsEnabled);
            });
        }

        public void RefreshMods()
        {
            if (SelectedInstance != null)
            {
                RefreshModsForInstance(SelectedInstance);
            }
            else
            {
                LoadInstances();
            }
        }

        public void RefreshModsForInstance(MinecraftInstance instance)
        {
            try
            {
                ActiveInstanceName = $"{instance.Name} ({instance.Loader} {instance.MinecraftVersion})";
                if (string.IsNullOrEmpty(instance.GameDirectory))
                {
                    _allMods.Clear();
                    ApplyFilter();
                    return;
                }
                var modsDir = Path.Combine(instance.GameDirectory, "mods");
                Directory.CreateDirectory(modsDir);

                var jarFiles = Directory.GetFiles(modsDir, "*.jar")
                    .Concat(Directory.GetFiles(modsDir, "*.jar.disabled"))
                    .ToArray();

                var list = new List<ModInfo>();
                foreach (var filePath in jarFiles)
                {
                    try
                    {
                        bool isEnabled = !filePath.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase);
                        var mod = ParseModJar(filePath, isEnabled, instance.MinecraftVersion ?? "1.21");
                        list.Add(mod);
                    }
                    catch { }
                }

                _allMods.Clear();
                _allMods.AddRange(list);

                ApplyFilter();
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Mods Error", ex.Message, NotificationType.Error);
            }
        }

        [RelayCommand]
        private void SelectCategory(string? category)
        {
            if (!string.IsNullOrEmpty(category))
            {
                SelectedCategory = category;
            }
        }

        [RelayCommand]
        private void SelectInstance(MinecraftInstance? instance)
        {
            if (instance == null) return;
            SelectedInstance = instance;
        }

        [RelayCommand]
        private void CreateNewInstance()
        {
            _main.NavigateTo("Versions");
        }

        [RelayCommand]
        private void BrowseModrinth()
        {
            _main.InstallationManagerVM.ActiveTab = "Mods";
            _main.NavigateTo("InstallationManager");
        }

        private static readonly HttpClient _modrinthHttp = new() { Timeout = TimeSpan.FromSeconds(5) };
        private static readonly string _iconsCacheDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Cache", "Icons");
        // Limit concurrent Modrinth icon fetches to avoid HTTP flooding
        private static readonly SemaphoreSlim _iconFetchSemaphore = new(4, 4);

        static ModsViewModel()
        {
            try { Directory.CreateDirectory(_iconsCacheDir); } catch { }
        }

        public static string DetectCategory(string name, string id, string desc)
        {
            var text = $"{name} {id} {desc}".ToLowerInvariant();
            if (text.Contains("sodium") || text.Contains("lithium") || text.Contains("ferritecore") || text.Contains("fps") || text.Contains("optifine") || text.Contains("performance") || text.Contains("krypton") || text.Contains("memory") || text.Contains("c2me") || text.Contains("fast") || text.Contains("smooth") || text.Contains("immediatelyfast"))
                return "PERFORMANCE";
            if (text.Contains("pvp") || text.Contains("combat") || text.Contains("crosshair") || text.Contains("reach") || text.Contains("hitbox") || text.Contains("armor") || text.Contains("keystroke") || text.Contains("freelook"))
                return "PVP";
            if (text.Contains("hud") || text.Contains("gui") || text.Contains("appleskin") || text.Contains("map") || text.Contains("minimap") || text.Contains("xaero") || text.Contains("journeymap") || text.Contains("waila") || text.Contains("jade") || text.Contains("health"))
                return "HUD";
            if (text.Contains("iris") || text.Contains("shader") || text.Contains("visual") || text.Contains("dynamic light") || text.Contains("lambdynamic") || text.Contains("continuity") || text.Contains("connected") || text.Contains("particle") || text.Contains("ambient"))
                return "VISUAL";
            if (text.Contains("world") || text.Contains("biome") || text.Contains("structure") || text.Contains("dungeon") || text.Contains("terralith") || text.Contains("generation"))
                return "WORLD";
            if (text.Contains("fabric-api") || text.Contains("cloth") || text.Contains("architectury") || text.Contains("library") || text.Contains("api") || text.Contains("lib") || text.Contains("kotlin") || text.Contains("yacl"))
                return "LIBRARY";
            return "UTILITY";
        }

        private ModInfo ParseModJar(string filePath, bool isEnabled, string mcVersion)
        {
            string fileName = Path.GetFileName(filePath);
            string cleanName = isEnabled ? fileName.Replace(".jar", "") : fileName.Replace(".jar.disabled", "");
            string version = "1.0";
            string author = "Unknown";
            string description = fileName;
            string loader = "Universal";
            string? iconPath = null;
            string? modId = null;
            string fileSize = "0 KB";

            try
            {
                var fi = new FileInfo(filePath);
                if (fi.Exists)
                {
                    fileSize = fi.Length > 1024 * 1024
                        ? $"{(fi.Length / (1024.0 * 1024.0)):F1} MB"
                        : $"{(fi.Length / 1024.0):F0} KB";
                }
            }
            catch { }

            try
            {
                using var zip = ZipFile.OpenRead(filePath);
                var fabricEntry = zip.GetEntry("fabric.mod.json");
                if (fabricEntry != null)
                {
                    using var s = fabricEntry.Open();
                    using var r = new StreamReader(s);
                    var jObj = JObject.Parse(r.ReadToEnd());
                    modId = jObj["id"]?.ToString();
                    cleanName = jObj["name"]?.ToString() ?? cleanName;
                    version = jObj["version"]?.ToString() ?? version;
                    description = jObj["description"]?.ToString() ?? description;
                    author = jObj["authors"]?.FirstOrDefault()?.ToString() ?? author;
                    loader = "Fabric";

                    var iconToken = jObj["icon"];
                    string? innerIconPath = null;
                    if (iconToken is JValue jv && jv.Value is string iconStr)
                    {
                        innerIconPath = iconStr;
                    }
                    else if (iconToken is JObject iconObj)
                    {
                        innerIconPath = iconObj.Properties().FirstOrDefault()?.Value.ToString();
                    }

                    if (!string.IsNullOrEmpty(innerIconPath))
                    {
                        iconPath = ExtractIconFromZip(zip, innerIconPath, cleanName);
                    }
                }
                else
                {
                    var quiltEntry = zip.GetEntry("quilt.mod.json");
                    if (quiltEntry != null)
                    {
                        using var s = quiltEntry.Open();
                        using var r = new StreamReader(s);
                        var jObj = JObject.Parse(r.ReadToEnd());
                        var meta = jObj["quilt_loader"]?["metadata"];
                        modId = meta?["id"]?.ToString();
                        cleanName = meta?["name"]?.ToString() ?? cleanName;
                        version = meta?["version"]?.ToString() ?? version;
                        description = meta?["description"]?.ToString() ?? description;
                        loader = "Quilt";

                        var iconStr = meta?["icon"]?.ToString();
                        if (!string.IsNullOrEmpty(iconStr))
                        {
                            iconPath = ExtractIconFromZip(zip, iconStr, cleanName);
                        }
                    }
                    else
                    {
                        var neoforgeEntry = zip.GetEntry("META-INF/neoforge.mods.toml");
                        var forgeEntry = zip.GetEntry("META-INF/mods.toml");
                        if (neoforgeEntry != null || forgeEntry != null)
                        {
                            var targetEntry = neoforgeEntry ?? forgeEntry;
                            using var s = targetEntry!.Open();
                            using var r = new StreamReader(s);
                            string toml = r.ReadToEnd();
                            loader = neoforgeEntry != null ? "NeoForge" : "Forge";
                            var nameMatch = System.Text.RegularExpressions.Regex.Match(toml, @"displayName\s*=\s*""([^""]+)""");
                            if (nameMatch.Success) cleanName = nameMatch.Groups[1].Value;
                            var verMatch = System.Text.RegularExpressions.Regex.Match(toml, @"version\s*=\s*""([^""]+)""");
                            if (verMatch.Success) version = verMatch.Groups[1].Value;
                            var descMatch = System.Text.RegularExpressions.Regex.Match(toml, @"description\s*=\s*'''([^']+)'''|description\s*=\s*""([^""]+)""");
                            if (descMatch.Success) description = descMatch.Groups[1].Success ? descMatch.Groups[1].Value.Trim() : descMatch.Groups[2].Value.Trim();
                            var logoMatch = System.Text.RegularExpressions.Regex.Match(toml, @"logoFile\s*=\s*""([^""]+)""");
                            if (logoMatch.Success)
                            {
                                iconPath = ExtractIconFromZip(zip, logoMatch.Groups[1].Value, cleanName);
                            }
                        }
                    }
                }

                // Fallback search any embedded icon/logo
                if (string.IsNullOrEmpty(iconPath))
                {
                    var anyIcon = zip.Entries.FirstOrDefault(e => e.FullName.EndsWith("icon.png", StringComparison.OrdinalIgnoreCase) ||
                                                                  e.FullName.EndsWith("logo.png", StringComparison.OrdinalIgnoreCase) ||
                                                                  e.FullName.Equals("pack.png", StringComparison.OrdinalIgnoreCase));
                    if (anyIcon != null)
                    {
                        iconPath = ExtractIconFromZip(zip, anyIcon.FullName, cleanName);
                    }
                }
            }
            catch { }

            var category = DetectCategory(cleanName, modId ?? "", description);

            // Dynamic Compatibility Evaluation
            var compatState = ModCompatibilityState.Compatible;
            string compatReason = string.Empty;

            if (SelectedInstance != null)
            {
                string instLoader = SelectedInstance.Loader?.ToString() ?? "Fabric";
                string instMc = SelectedInstance.MinecraftVersion ?? mcVersion;

                if (!string.Equals(loader, "Universal", StringComparison.OrdinalIgnoreCase))
                {
                    bool loaderMatches = string.Equals(loader, instLoader, StringComparison.OrdinalIgnoreCase)
                        || (loader.Equals("Fabric", StringComparison.OrdinalIgnoreCase) && instLoader.Equals("Quilt", StringComparison.OrdinalIgnoreCase))
                        || (loader.Equals("Quilt", StringComparison.OrdinalIgnoreCase) && instLoader.Equals("Fabric", StringComparison.OrdinalIgnoreCase));

                    if (!loaderMatches)
                    {
                        compatState = ModCompatibilityState.UnsupportedLoader;
                        compatReason = $"Mod requires {loader}, instance is {instLoader}";
                    }
                }
            }

            var modInfo = new ModInfo
            {
                Id = filePath,
                Name = cleanName,
                Version = version,
                MinecraftVersion = mcVersion,
                Author = author,
                Description = description,
                Category = category,
                FileName = fileName,
                FilePath = filePath,
                FileSizeFormatted = fileSize,
                ModLoader = loader,
                IsEnabled = isEnabled,
                IconPath = iconPath,
                Compatibility = compatState,
                CompatibilityReason = compatReason
            };

            // If no embedded icon found, resolve asynchronously from Modrinth CDN
            if (string.IsNullOrEmpty(iconPath))
            {
                QueueModrinthIconFetch(modInfo, modId ?? cleanName);
            }

            return modInfo;
        }

        private static string? ExtractIconFromZip(ZipArchive zip, string entryPath, string modName)
        {
            try
            {
                entryPath = entryPath.TrimStart('/', '\\');
                var entry = zip.GetEntry(entryPath) 
                         ?? zip.Entries.FirstOrDefault(e => e.FullName.Equals(entryPath, StringComparison.OrdinalIgnoreCase))
                         ?? zip.Entries.FirstOrDefault(e => e.FullName.EndsWith(Path.GetFileName(entryPath), StringComparison.OrdinalIgnoreCase));
                if (entry != null)
                {
                    string safeName = string.Join("_", modName.Split(Path.GetInvalidFileNameChars()));
                    string dest = Path.Combine(_iconsCacheDir, $"{safeName}.png");
                    using var inStream = entry.Open();
                    using var outStream = File.Create(dest);
                    inStream.CopyTo(outStream);
                    return dest;
                }
            }
            catch { }
            return null;
        }

        private void QueueModrinthIconFetch(ModInfo mod, string query)
        {
            _ = Task.Run(async () =>
            {
                // Throttle concurrent requests
                await _iconFetchSemaphore.WaitAsync();
                try
                {
                    string url = $"https://api.modrinth.com/v2/search?query={Uri.EscapeDataString(query)}&facets=[[\"project_type:mod\"]]&limit=1";
                    var res = await _modrinthHttp.GetStringAsync(url);
                    using var doc = System.Text.Json.JsonDocument.Parse(res);
                    if (doc.RootElement.TryGetProperty("hits", out var hits) && hits.GetArrayLength() > 0)
                    {
                        var firstHit = hits[0];
                        if (firstHit.TryGetProperty("icon_url", out var iconProp))
                        {
                            string iconUrl = iconProp.GetString() ?? "";
                            if (!string.IsNullOrEmpty(iconUrl))
                            {
                                Dispatch(() =>
                                {
                                    mod.IconPath = iconUrl;
                                });
                            }
                        }
                    }
                }
                catch { }
                finally
                {
                    _iconFetchSemaphore.Release();
                }
            });
        }

        [RelayCommand]
        private void ToggleMod(object? modIdObj)
        {
            if (modIdObj == null) return;
            ModInfo? mod = modIdObj as ModInfo;
            if (mod == null && modIdObj is string modIdStr)
            {
                mod = _allMods.FirstOrDefault(m => m.Name == modIdStr || m.Id == modIdStr || m.FilePath == modIdStr);
            }
            if (mod == null) return;

            string currentPath = mod.FilePath ?? string.Empty;

            // Determine if the mod is currently disabled on disk
            bool isCurrentlyDisabledOnDisk = currentPath.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)
                || (!File.Exists(currentPath) && File.Exists(currentPath + ".disabled"));

            if (isCurrentlyDisabledOnDisk)
            {
                // Action: ENABLE the mod (rename .disabled -> .jar)
                try
                {
                    string disabledFile = currentPath.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)
                        ? currentPath
                        : currentPath + ".disabled";

                    if (!File.Exists(disabledFile))
                    {
                        _main.ShowNotification("Mod File Not Found", $"Could not find file on disk: {Path.GetFileName(disabledFile)}", NotificationType.Warning);
                        return;
                    }

                    string enabledFile = disabledFile.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)
                        ? disabledFile.Substring(0, disabledFile.Length - ".disabled".Length)
                        : disabledFile;

                    if (File.Exists(enabledFile))
                    {
                        File.Delete(enabledFile);
                    }

                    File.Move(disabledFile, enabledFile);
                    mod.FilePath = enabledFile;
                    mod.FileName = Path.GetFileName(enabledFile);
                    mod.Id = enabledFile;
                    mod.IsEnabled = true;

                    CrashLogger.LogMessage($"[Mods] Enabled mod '{mod.Name}' → {mod.FileName}");
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException("ToggleMod (Enable)", ex);
                    _main.ShowNotification("Mod Enable Failed", ex.Message, NotificationType.Error);
                    return;
                }
            }
            else
            {
                // Action: DISABLE the mod (rename .jar -> .jar.disabled)
                try
                {
                    string enabledFile = currentPath;
                    if (!File.Exists(enabledFile))
                    {
                        _main.ShowNotification("Mod File Not Found", $"Could not find file on disk: {Path.GetFileName(enabledFile)}", NotificationType.Warning);
                        return;
                    }

                    string disabledFile = enabledFile.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase)
                        ? enabledFile
                        : enabledFile + ".disabled";

                    if (File.Exists(disabledFile))
                    {
                        File.Delete(disabledFile);
                    }

                    File.Move(enabledFile, disabledFile);
                    mod.FilePath = disabledFile;
                    mod.FileName = Path.GetFileName(disabledFile);
                    mod.Id = disabledFile;
                    mod.IsEnabled = false;

                    CrashLogger.LogMessage($"[Mods] Disabled mod '{mod.Name}' → {mod.FileName}");
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException("ToggleMod (Disable)", ex);
                    _main.ShowNotification("Mod Disable Failed", ex.Message, NotificationType.Error);
                    return;
                }
            }

            TotalModCount = _allMods.Count;
            EnabledModCount = _allMods.Count(m => m.IsEnabled);

            _main.ShowNotification(
                mod.IsEnabled ? "Mod Enabled" : "Mod Disabled",
                $"{mod.Name} is now {(mod.IsEnabled ? "enabled" : "disabled")}.",
                mod.IsEnabled ? NotificationType.Success : NotificationType.Info);
        }

        [RelayCommand]
        private void DeleteMod(object? modIdObj)
        {
            if (modIdObj == null) return;
            ModInfo? mod = modIdObj as ModInfo;
            if (mod == null && modIdObj is string modIdStr)
            {
                mod = Mods.FirstOrDefault(m => m.Name == modIdStr || m.Id == modIdStr || m.FilePath == modIdStr);
            }
            if (mod == null) return;

            try
            {
                if (!string.IsNullOrEmpty(mod.FilePath) && File.Exists(mod.FilePath))
                {
                    File.Delete(mod.FilePath);
                }
                _allMods.RemoveAll(m => m.FilePath == mod.FilePath || m.Name == mod.Name);
                Mods.Remove(mod);
                HasMods = Mods.Count > 0;
                TotalModCount = _allMods.Count;
                EnabledModCount = _allMods.Count(m => m.IsEnabled);
                _main.ShowNotification("Mod Removed", $"Deleted {mod.Name} from instance.", NotificationType.Info);
            }
            catch (Exception ex)
            {
                _main.ShowNotification("Delete Failed", ex.Message, NotificationType.Error);
            }
        }

        [RelayCommand]
        private void AddMod()
        {
            var instance = SelectedInstance ?? _instanceService.GetActiveInstance();
            if (instance == null)
            {
                _main.ShowNotification("No Instance", "Select an active instance first.", NotificationType.Warning);
                return;
            }

            var dialog = new OpenFileDialog
            {
                Title = "Select Minecraft Mod (.jar)",
                Filter = "Minecraft Mods (*.jar)|*.jar|All Files (*.*)|*.*",
                Multiselect = true
            };

            if (dialog.ShowDialog() == true)
            {
                var modsDir = Path.Combine(instance.GameDirectory, "mods");
                Directory.CreateDirectory(modsDir);

                int copied = 0;
                foreach (var sourceFile in dialog.FileNames)
                {
                    try
                    {
                        var destFile = Path.Combine(modsDir, Path.GetFileName(sourceFile));
                        File.Copy(sourceFile, destFile, overwrite: true);
                        copied++;
                    }
                    catch { }
                }

                RefreshMods();
                _main.ShowNotification("Mods Added", $"Successfully added {copied} mod(s) to {instance.Name}.", NotificationType.Success);
            }
        }

        [RelayCommand]
        private void OpenModsFolder()
        {
            var instance = SelectedInstance ?? _instanceService.GetActiveInstance();
            if (instance == null) return;

            var modsDir = Path.Combine(instance.GameDirectory, "mods");
            Directory.CreateDirectory(modsDir);

            try
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = "explorer.exe",
                    Arguments = $"\"{modsDir}\"",
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
