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
        private string _activeInstanceName = "No Instance Selected";

        [ObservableProperty]
        private bool _hasMods = false;

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

        partial void OnSearchQueryChanged(string value)
        {
            ApplyFilter();
        }

        private void ApplyFilter()
        {
            Dispatch(() =>
            {
                Mods.Clear();
                var filtered = string.IsNullOrWhiteSpace(SearchQuery)
                    ? _allMods
                    : _allMods.Where(m => m.Name.Contains(SearchQuery, StringComparison.OrdinalIgnoreCase) ||
                                          m.Description.Contains(SearchQuery, StringComparison.OrdinalIgnoreCase) ||
                                          m.Author.Contains(SearchQuery, StringComparison.OrdinalIgnoreCase));

                foreach (var m in filtered) Mods.Add(m);
                HasMods = Mods.Count > 0;
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
                var modsDir = Path.Combine(instance.GameDirectory, "mods");
                Directory.CreateDirectory(modsDir);

                var jarFiles = Directory.GetFiles(modsDir, "*.jar")
                    .Concat(Directory.GetFiles(modsDir, "*.jar.disabled"))
                    .ToArray();

                var list = new List<ModInfo>();
                foreach (var filePath in jarFiles)
                {
                    bool isEnabled = !filePath.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase);
                    var mod = ParseModJar(filePath, isEnabled);
                    list.Add(mod);
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

        static ModsViewModel()
        {
            try { Directory.CreateDirectory(_iconsCacheDir); } catch { }
        }

        private ModInfo ParseModJar(string filePath, bool isEnabled)
        {
            string fileName = Path.GetFileName(filePath);
            string cleanName = isEnabled ? fileName.Replace(".jar", "") : fileName.Replace(".jar.disabled", "");
            string version = "1.0";
            string author = "Unknown";
            string description = fileName;
            string loader = "Universal";
            string? iconPath = null;
            string? modId = null;

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
                        var forgeEntry = zip.GetEntry("META-INF/mods.toml");
                        if (forgeEntry != null)
                        {
                            using var s = forgeEntry.Open();
                            using var r = new StreamReader(s);
                            string toml = r.ReadToEnd();
                            loader = "Forge";
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

            var modInfo = new ModInfo
            {
                Id = filePath,
                Name = cleanName,
                Version = version,
                Author = author,
                Description = description,
                ModLoader = loader,
                IsEnabled = isEnabled,
                IconPath = iconPath
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
            });
        }

        [RelayCommand]
        private void ToggleMod(object? modIdObj)
        {
            var modId = modIdObj?.ToString();
            if (string.IsNullOrEmpty(modId)) return;

            var mod = Mods.FirstOrDefault(m => m.Name == modId || m.Id == modId);
            if (mod != null)
            {
                if (File.Exists(mod.Id))
                {
                    try
                    {
                        if (mod.IsEnabled)
                        {
                            // Disable -> rename to .disabled
                            var disabledPath = mod.Id + ".disabled";
                            if (File.Exists(disabledPath)) File.Delete(disabledPath);
                            File.Move(mod.Id, disabledPath);
                            mod.Id = disabledPath;
                            mod.IsEnabled = false;
                        }
                        else
                        {
                            // Enable -> rename to .jar
                            var enabledPath = mod.Id.Replace(".jar.disabled", ".jar");
                            if (File.Exists(enabledPath)) File.Delete(enabledPath);
                            File.Move(mod.Id, enabledPath);
                            mod.Id = enabledPath;
                            mod.IsEnabled = true;
                        }
                    }
                    catch (Exception ex)
                    {
                        _main.ShowNotification("Mod Toggle Failed", ex.Message, NotificationType.Error);
                        return;
                    }
                }
                else
                {
                    mod.IsEnabled = !mod.IsEnabled;
                }

                OnPropertyChanged(nameof(Mods));
                _main.ShowNotification(
                    mod.IsEnabled ? "Mod Enabled" : "Mod Disabled",
                    $"{mod.Name} is now {(mod.IsEnabled ? "enabled" : "disabled")}.",
                    mod.IsEnabled ? NotificationType.Success : NotificationType.Info);
            }
        }

        [RelayCommand]
        private void DeleteMod(object? modIdObj)
        {
            var modId = modIdObj?.ToString();
            if (string.IsNullOrEmpty(modId)) return;

            var mod = Mods.FirstOrDefault(m => m.Name == modId || m.Id == modId);
            if (mod != null)
            {
                try
                {
                    if (File.Exists(mod.Id))
                    {
                        File.Delete(mod.Id);
                    }
                    _allMods.RemoveAll(m => m.Id == mod.Id || m.Name == mod.Name);
                    Mods.Remove(mod);
                    HasMods = Mods.Count > 0;
                    _main.ShowNotification("Mod Removed", $"Deleted {mod.Name} from instance.", NotificationType.Info);
                }
                catch (Exception ex)
                {
                    _main.ShowNotification("Delete Failed", ex.Message, NotificationType.Error);
                }
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
