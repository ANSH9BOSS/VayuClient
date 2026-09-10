using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Instance;
using VayuClient.Services.Launch;
using VayuClient.Services.Modpack;
using VayuClient.Services.Version;

namespace VayuClient.Views
{
    public partial class ModpackConverterDialog : Window
    {
        private MinecraftInstance? _sourceInstance;
        private readonly IModpackConverterService? _converterService;
        private readonly IInstanceService? _instanceService;
        private readonly IVersionService? _versionService;
        private CancellationTokenSource? _cts;
        private ConversionResult? _lastResult;

        public static readonly string[] CommonMinecraftVersions = new[]
        {
            "26.2", "26.2.0", "26.1.2", "26.1", "26",
            "1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6", "1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
            "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
            "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
            "1.18.2", "1.18.1", "1.18",
            "1.17.1", "1.17",
            "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
            "1.15.2", "1.15.1", "1.15",
            "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
            "1.13.2", "1.13.1", "1.13",
            "1.12.2", "1.12.1", "1.12",
            "1.11.2", "1.11.1", "1.11",
            "1.10.2", "1.10.1", "1.10",
            "1.9.4", "1.9.3", "1.9.2", "1.9.1", "1.9",
            "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8.2", "1.8.1", "1.8",
            "1.7.10", "1.7.9", "1.7.8", "1.7.7", "1.7.6", "1.7.5", "1.7.4", "1.7.2",
            "1.6.4", "1.6.2", "1.6.1",
            "1.5.2", "1.5.1",
            "1.4.7", "1.4.6", "1.4.5", "1.4.4", "1.4.2",
            "1.3.2", "1.3.1",
            "1.2.5", "1.2.4", "1.2.3", "1.2.2", "1.2.1",
            "1.1", "1.0.0"
        };

        public static readonly string[] CommonLoaders = new[]
        {
            "Fabric",
            "NeoForge",
            "Forge",
            "Quilt"
        };

        public ModpackConverterDialog()
        {
            InitializeComponent();
            _converterService = ServiceLocator.Resolve<IModpackConverterService>();
            _instanceService = ServiceLocator.Resolve<IInstanceService>();
            _versionService = ServiceLocator.Resolve<IVersionService>();
        }

        public void Initialize(MinecraftInstance sourceInstance)
        {
            _sourceInstance = sourceInstance;
            if (sourceInstance == null) return;

            TxtSourceName.Text = sourceInstance.Name;
            TxtSourceSubtitle.Text = $"Minecraft {sourceInstance.MinecraftVersion} {sourceInstance.Loader} • {sourceInstance.RamMB} MB RAM";

            int modCount = CountModsInInstance(sourceInstance);
            TxtSourceModCount.Text = $"{modCount} Mods";

            // Populate Loaders
            CmbTargetLoader.ItemsSource = CommonLoaders;
            CmbTargetLoader.SelectedItem = !string.IsNullOrEmpty(sourceInstance.Loader) && CommonLoaders.Contains(sourceInstance.Loader)
                ? sourceInstance.Loader
                : "Fabric";

            // Populate Versions
            var versionsList = new List<string>(CommonMinecraftVersions);
            if (!versionsList.Contains(sourceInstance.MinecraftVersion))
            {
                versionsList.Insert(0, sourceInstance.MinecraftVersion);
            }
            CmbTargetVersion.ItemsSource = versionsList;

            // Pick a different default version if current is in list
            string defaultTarget = versionsList.FirstOrDefault(v => v != sourceInstance.MinecraftVersion) ?? "1.20.1";
            CmbTargetVersion.SelectedItem = defaultTarget;

            UpdateNewInstanceName();

            SliderRam.Value = sourceInstance.RamMB > 0 ? sourceInstance.RamMB : 4096;
            TxtAllocatedRamLabel.Text = $"ALLOCATED RAM: {(int)SliderRam.Value} MB";

            // Asynchronously sync and expand with full Mojang manifest release catalog
            _ = LoadManifestVersionsAsync(sourceInstance);
        }

        private async Task LoadManifestVersionsAsync(MinecraftInstance sourceInstance)
        {
            try
            {
                if (_versionService == null) return;
                var manifestVersions = await _versionService.GetManifestVersionsAsync();
                if (manifestVersions != null && manifestVersions.Count > 0)
                {
                    var releaseIds = manifestVersions
                        .Where(v => v.Type == "release" || v.Id.StartsWith("1.") || v.Id.StartsWith("26"))
                        .Select(v => v.Id)
                        .Distinct()
                        .ToList();

                    if (releaseIds.Count > 0)
                    {
                        var merged = new List<string>(CommonMinecraftVersions);
                        foreach (var id in releaseIds)
                        {
                            if (!merged.Contains(id)) merged.Add(id);
                        }

                        Dispatcher.Invoke(() =>
                        {
                            var currentSelected = CmbTargetVersion.SelectedItem?.ToString();
                            var fullList = new List<string>(merged);
                            if (!string.IsNullOrEmpty(sourceInstance.MinecraftVersion) && !fullList.Contains(sourceInstance.MinecraftVersion))
                            {
                                fullList.Insert(0, sourceInstance.MinecraftVersion);
                            }
                            CmbTargetVersion.ItemsSource = fullList;
                            if (!string.IsNullOrEmpty(currentSelected) && fullList.Contains(currentSelected))
                            {
                                CmbTargetVersion.SelectedItem = currentSelected;
                            }
                        });
                    }
                }
            }
            catch { }
        }

        public static void ShowDialog(MinecraftInstance sourceInstance, Window? owner = null)
        {
            var dialog = new ModpackConverterDialog();
            if (owner != null)
            {
                dialog.Owner = owner;
            }
            dialog.Initialize(sourceInstance);
            dialog.ShowDialog();
        }

        private static int CountModsInInstance(MinecraftInstance instance)
        {
            if (string.IsNullOrEmpty(instance.GameDirectory)) return 0;
            var modsDir = Path.Combine(instance.GameDirectory, "mods");
            if (!Directory.Exists(modsDir)) return 0;
            return Directory.GetFiles(modsDir, "*.jar").Length + Directory.GetFiles(modsDir, "*.jar.disabled").Length;
        }

        private void UpdateNewInstanceName()
        {
            if (_sourceInstance == null) return;
            string targetVer = CmbTargetVersion.SelectedItem?.ToString() ?? "1.20.1";
            string targetLoader = CmbTargetLoader.SelectedItem?.ToString() ?? "Fabric";
            TxtNewInstanceName.Text = $"{_sourceInstance.Name} ({targetVer} {targetLoader})";
        }

        private void CmbTargetVersion_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            UpdateNewInstanceName();
        }

        private void CmbTargetLoader_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            UpdateNewInstanceName();
        }

        private void SliderRam_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
        {
            if (TxtAllocatedRamLabel != null)
            {
                TxtAllocatedRamLabel.Text = $"ALLOCATED RAM: {(int)e.NewValue} MB";
            }
        }

        private async void StartConvert_Click(object sender, RoutedEventArgs e)
        {
            if (_sourceInstance == null || _converterService == null) return;

            string targetVersion = CmbTargetVersion.SelectedItem?.ToString() ?? string.Empty;
            string targetLoader = CmbTargetLoader.SelectedItem?.ToString() ?? "Fabric";
            string newName = TxtNewInstanceName.Text.Trim();

            if (string.IsNullOrWhiteSpace(targetVersion))
            {
                MessageBox.Show("Please select a target Minecraft version.", "Version Required", MessageBoxButton.OK, MessageBoxImage.Warning);
                return;
            }

            if (string.IsNullOrWhiteSpace(newName))
            {
                newName = $"{_sourceInstance.Name} ({targetVersion} {targetLoader})";
            }

            var options = new ConversionOptions
            {
                TargetMinecraftVersion = targetVersion,
                TargetLoader = targetLoader,
                NewInstanceName = newName,
                CopyResourcePacks = ChkCopyResourcePacks.IsChecked == true,
                CopyShaderPacks = ChkCopyShaderPacks.IsChecked == true,
                CopyOptions = ChkCopyOptions.IsChecked == true,
                RamMB = (int)SliderRam.Value
            };

            // Switch to Converting View
            ConfigView.Visibility = Visibility.Collapsed;
            ConfigActions.Visibility = Visibility.Collapsed;
            ConvertingView.Visibility = Visibility.Visible;
            ConvertingActions.Visibility = Visibility.Visible;
            ResultsView.Visibility = Visibility.Collapsed;
            ResultsActions.Visibility = Visibility.Collapsed;

            _cts = new CancellationTokenSource();

            var progress = new Progress<ConversionProgressInfo>(p =>
            {
                Dispatcher.Invoke(() =>
                {
                    ProgressBarConversion.Value = p.Percent;
                    TxtProgressPercent.Text = $"{(int)p.Percent}%";
                    TxtProgressOperation.Text = p.CurrentOperation;
                    TxtProgressDetail.Text = !string.IsNullOrEmpty(p.CurrentItem) ? p.CurrentItem : p.Phase.ToString();
                });
            });

            try
            {
                _lastResult = await _converterService.ConvertInstanceAsync(_sourceInstance, options, progress, _cts.Token);
                ShowResults(_lastResult);
            }
            catch (OperationCanceledException)
            {
                TxtProgressOperation.Text = "Conversion cancelled by user.";
                await Task.Delay(800);
                ConfigView.Visibility = Visibility.Visible;
                ConfigActions.Visibility = Visibility.Visible;
                ConvertingView.Visibility = Visibility.Collapsed;
                ConvertingActions.Visibility = Visibility.Collapsed;
            }
            catch (Exception ex)
            {
                MessageBox.Show($"Conversion failed: {ex.Message}", "Conversion Error", MessageBoxButton.OK, MessageBoxImage.Error);
                ConfigView.Visibility = Visibility.Visible;
                ConfigActions.Visibility = Visibility.Visible;
                ConvertingView.Visibility = Visibility.Collapsed;
                ConvertingActions.Visibility = Visibility.Collapsed;
            }
        }

        private void ShowResults(ConversionResult result)
        {
            ConvertingView.Visibility = Visibility.Collapsed;
            ConvertingActions.Visibility = Visibility.Collapsed;
            ResultsView.Visibility = Visibility.Visible;
            ResultsActions.Visibility = Visibility.Visible;

            TxtPortedCount.Text = result.PortedMods.Count.ToString();
            TxtMissingCount.Text = result.MissingMods.Count.ToString();
            TxtResourcePacksCount.Text = (result.CopiedResourcePacks.Count + result.CopiedShaderPacks.Count).ToString();

            RbTabMissing.Content = $"⚠️ Missing Mods ({result.MissingMods.Count})";
            RbTabPorted.Content = $"✓ Ported Mods ({result.PortedMods.Count})";
            RbTabPacks.Content = $"🎨 Resource Packs ({result.CopiedResourcePacks.Count})";

            ItemsMissingMods.ItemsSource = result.MissingMods;
            TxtNoMissingMods.Visibility = result.MissingMods.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

            ItemsPortedMods.ItemsSource = result.PortedMods;

            var allPacks = new List<string>(result.CopiedResourcePacks);
            if (result.CopiedShaderPacks.Count > 0)
            {
                allPacks.AddRange(result.CopiedShaderPacks.Select(s => $"[Shader] {s}"));
            }
            ItemsResourcePacks.ItemsSource = allPacks;
            TxtNoResourcePacks.Visibility = allPacks.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

            // Select tab with most relevance
            if (result.MissingMods.Count > 0)
            {
                RbTabMissing.IsChecked = true;
            }
            else
            {
                RbTabPorted.IsChecked = true;
            }
        }

        private void CancelConvert_Click(object sender, RoutedEventArgs e)
        {
            _cts?.Cancel();
        }

        private void RbTabMissing_Checked(object sender, RoutedEventArgs e)
        {
            MissingModsContainer.Visibility = Visibility.Visible;
            PortedModsContainer.Visibility = Visibility.Collapsed;
            ResourcePacksContainer.Visibility = Visibility.Collapsed;
        }

        private void RbTabPorted_Checked(object sender, RoutedEventArgs e)
        {
            MissingModsContainer.Visibility = Visibility.Collapsed;
            PortedModsContainer.Visibility = Visibility.Visible;
            ResourcePacksContainer.Visibility = Visibility.Collapsed;
        }

        private void RbTabPacks_Checked(object sender, RoutedEventArgs e)
        {
            MissingModsContainer.Visibility = Visibility.Collapsed;
            PortedModsContainer.Visibility = Visibility.Collapsed;
            ResourcePacksContainer.Visibility = Visibility.Visible;
        }

        private void SearchModrinth_Click(object sender, RoutedEventArgs e)
        {
            if (sender is Button btn && btn.Tag is string url && !string.IsNullOrEmpty(url))
            {
                try
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = url,
                        UseShellExecute = true
                    });
                }
                catch { }
            }
        }

        private void OpenModsFolder_Click(object sender, RoutedEventArgs e)
        {
            var targetInstance = _lastResult?.ConvertedInstance ?? _sourceInstance;
            if (targetInstance != null && !string.IsNullOrEmpty(targetInstance.GameDirectory))
            {
                var modsDir = Path.Combine(targetInstance.GameDirectory, "mods");
                Directory.CreateDirectory(modsDir);
                try
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = modsDir,
                        UseShellExecute = true
                    });
                }
                catch { }
            }
        }

        private void PlayConverted_Click(object sender, RoutedEventArgs e)
        {
            if (_lastResult?.ConvertedInstance != null && _instanceService != null)
            {
                _instanceService.SetActiveInstance(_lastResult.ConvertedInstance.InstanceId);
                var launchService = ServiceLocator.Resolve<ILaunchService>();
                if (launchService != null)
                {
                    _ = launchService.LaunchInstanceAsync(_lastResult.ConvertedInstance.InstanceId);
                }
                Close();
            }
        }

        private void Close_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private void Window_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.ChangedButton == MouseButton.Left)
            {
                DragMove();
            }
        }
    }
}
