using System;
using System.Diagnostics;
using System.IO;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Input;
using VayuClientSetup.Services;

namespace VayuClientSetup
{
    public partial class MainWindow : Window
    {
        private readonly InstallerService _installer = new();
        private ExistingInstallInfo? _existingInfo;
        private string _installedExePath = string.Empty;
        private bool _isUpdateMode;

        public MainWindow()
        {
            InitializeComponent();
            TxtInstallPath.Text = InstallerService.GetDefaultInstallPath();

            if (App.IsUninstallMode)
            {
                TitleText.Text = "VayuClient Uninstaller";
                ShowScreen(ScreenUninstall);
            }
            else
            {
                // Detect existing installation
                _existingInfo = InstallerService.DetectExistingInstallation();
                if (_existingInfo != null && _existingInfo.Exists)
                {
                    SetupUpdateScreen(_existingInfo);
                    ShowScreen(ScreenUpdate);
                }
                else
                {
                    _isUpdateMode = false;
                    ShowScreen(ScreenWelcome);
                }
            }

            ContentRendered += (s, e) =>
            {
                SetupProfiler.Record("Installer UI interactive");
                SetupProfiler.Record("Startup complete");
                SetupProfiler.Flush();
            };
        }

        private void SetupUpdateScreen(ExistingInstallInfo existing)
        {
            _isUpdateMode = true;
            TxtInstallPath.Text = existing.InstallPath;

            var installedVer = existing.Version;
            var setupVer = InstallerService.CurrentSetupVersion;

            var comparison = CompareVersions(installedVer, setupVer);

            if (comparison == 0) // Exact same version already installed
            {
                TxtUpdateHeader.Text = "VayuClient is Already Installed";
                TxtUpdateSubheader.Text = "VayuClient is installed and up to date on your computer.";

                CardVersionComparison.Visibility = Visibility.Collapsed;
                CardAlreadyInstalled.Visibility = Visibility.Visible;
                TxtInstalledVerBadge.Text = $"VayuClient v{installedVer}";
                TxtInstalledPathDisplay.Text = $"Installed at: {existing.InstallPath}";

                PanelUpdateButtons.Visibility = Visibility.Collapsed;
                PanelUpToDateButtons.Visibility = Visibility.Visible;
            }
            else if (comparison < 0) // Older version installed -> Update available
            {
                TxtUpdateHeader.Text = "Update VayuClient";
                TxtUpdateSubheader.Text = "An update is available for your VayuClient installation.";

                CardVersionComparison.Visibility = Visibility.Visible;
                CardAlreadyInstalled.Visibility = Visibility.Collapsed;
                TxtInstalledVer.Text = installedVer;
                TxtAvailableVer.Text = setupVer;

                PanelUpdateButtons.Visibility = Visibility.Visible;
                PanelUpToDateButtons.Visibility = Visibility.Collapsed;
            }
            else // Newer version already installed
            {
                TxtUpdateHeader.Text = "Newer Version Detected";
                TxtUpdateSubheader.Text = $"You already have a newer version of VayuClient (v{installedVer}) installed.";

                CardVersionComparison.Visibility = Visibility.Visible;
                CardAlreadyInstalled.Visibility = Visibility.Collapsed;
                TxtInstalledVer.Text = installedVer;
                TxtAvailableVer.Text = setupVer;

                PanelUpdateButtons.Visibility = Visibility.Collapsed;
                PanelUpToDateButtons.Visibility = Visibility.Visible;
            }
        }

        private static int CompareVersions(string v1, string v2)
        {
            if (Version.TryParse(v1, out var ver1) && Version.TryParse(v2, out var ver2))
            {
                return ver1.CompareTo(ver2);
            }
            return string.Compare(v1, v2, StringComparison.OrdinalIgnoreCase);
        }

        private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
        {
            if (e.LeftButton == MouseButtonState.Pressed)
            {
                try { DragMove(); } catch { }
            }
        }

        private void Close_Click(object sender, RoutedEventArgs e)
        {
            Close();
        }

        private void WelcomeNext_Click(object sender, RoutedEventArgs e)
        {
            ShowScreen(ScreenOptions);
        }

        private void OptionsBack_Click(object sender, RoutedEventArgs e)
        {
            if (_isUpdateMode)
            {
                ShowScreen(ScreenUpdate);
            }
            else
            {
                ShowScreen(ScreenWelcome);
            }
        }

        private void LaunchInstalled_Click(object sender, RoutedEventArgs e)
        {
            var exe = Path.Combine(_existingInfo?.InstallPath ?? InstallerService.GetDefaultInstallPath(), "VayuClient.exe");
            if (File.Exists(exe))
            {
                try
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = exe,
                        WorkingDirectory = Path.GetDirectoryName(exe),
                        UseShellExecute = true
                    });
                }
                catch { }
            }
            Close();
        }

        private void UninstallFromUpdate_Click(object sender, RoutedEventArgs e)
        {
            TitleText.Text = "VayuClient Uninstaller";
            ShowScreen(ScreenUninstall);
        }

        private void Browse_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new System.Windows.Forms.FolderBrowserDialog
            {
                Description = "Select VayuClient Installation Directory",
                UseDescriptionForTitle = true,
                SelectedPath = TxtInstallPath.Text
            };

            if (dialog.ShowDialog() == System.Windows.Forms.DialogResult.OK)
            {
                TxtInstallPath.Text = dialog.SelectedPath;
            }
        }

        private async void StartUpdate_Click(object sender, RoutedEventArgs e)
        {
            // Check if process is running
            if (Process.GetProcessesByName("VayuClient").Length > 0)
            {
                ModalRunningProcess.Visibility = Visibility.Visible;
                return;
            }

            await ExecuteInstallationAsync();
        }

        private async void ConfirmProcessClose_Click(object sender, RoutedEventArgs e)
        {
            ModalRunningProcess.Visibility = Visibility.Collapsed;
            await InstallerService.CloseRunningProcessesAsync(3000);
            await ExecuteInstallationAsync();
        }

        private void CancelProcessClose_Click(object sender, RoutedEventArgs e)
        {
            ModalRunningProcess.Visibility = Visibility.Collapsed;
        }

        private async void StartInstall_Click(object sender, RoutedEventArgs e)
        {
            if (Process.GetProcessesByName("VayuClient").Length > 0)
            {
                ModalRunningProcess.Visibility = Visibility.Visible;
                return;
            }

            await ExecuteInstallationAsync();
        }

        private async Task ExecuteInstallationAsync()
        {
            var targetDir = TxtInstallPath.Text.Trim();
            if (string.IsNullOrEmpty(targetDir))
            {
                targetDir = InstallerService.GetDefaultInstallPath();
                TxtInstallPath.Text = targetDir;
            }

            _installedExePath = Path.Combine(targetDir, "VayuClient.exe");
            TxtProgressTitle.Text = _isUpdateMode ? "Updating VayuClient..." : "Installing VayuClient...";
            TxtCompletedHeader.Text = _isUpdateMode ? "VayuClient Updated Successfully" : "VayuClient Installed Successfully";
            ShowScreen(ScreenProgress);

            try
            {
                await _installer.InstallOrUpdateAsync(
                    targetDir,
                    ChkDesktopShortcut.IsChecked == true,
                    ChkStartMenuShortcut.IsChecked == true,
                    (percent, status) =>
                    {
                        Dispatcher.Invoke(() =>
                        {
                            TxtProgressStatus.Text = status;
                            TxtProgressPercent.Text = $"{percent}%";

                            double totalWidth = 600;
                            ProgressBarFill.Width = Math.Clamp((percent / 100.0) * totalWidth, 0, totalWidth);
                        });
                    });

                ShowScreen(ScreenCompleted);
            }
            catch (Exception ex)
            {
                System.Windows.MessageBox.Show($"Installation encountered an error: {ex.Message}", "VayuClient Setup Error", MessageBoxButton.OK, MessageBoxImage.Error);
                ShowScreen(_isUpdateMode ? ScreenUpdate : ScreenOptions);
            }
        }

        private async void StartUninstall_Click(object sender, RoutedEventArgs e)
        {
            ShowScreen(ScreenProgress);
            TxtProgressTitle.Text = "Uninstalling VayuClient...";

            try
            {
                await _installer.UninstallAsync((percent, status) =>
                {
                    Dispatcher.Invoke(() =>
                    {
                        TxtProgressStatus.Text = status;
                        TxtProgressPercent.Text = $"{percent}%";
                        double totalWidth = 600;
                        ProgressBarFill.Width = Math.Clamp((percent / 100.0) * totalWidth, 0, totalWidth);
                    });
                });

                System.Windows.MessageBox.Show("VayuClient has been cleanly uninstalled.\nYour profiles and Minecraft data in %APPDATA%\\VayuClient remain safe.", "VayuClient Setup", MessageBoxButton.OK, MessageBoxImage.Information);
                Close();
            }
            catch (Exception ex)
            {
                System.Windows.MessageBox.Show($"Uninstallation error: {ex.Message}", "VayuClient Setup Error", MessageBoxButton.OK, MessageBoxImage.Error);
                Close();
            }
        }

        private void Finish_Click(object sender, RoutedEventArgs e)
        {
            if (ChkLaunchApp.IsChecked == true && File.Exists(_installedExePath))
            {
                try
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = _installedExePath,
                        WorkingDirectory = Path.GetDirectoryName(_installedExePath),
                        UseShellExecute = true
                    });
                }
                catch { }
            }

            Close();
        }

        private void ShowScreen(UIElement screenToShow)
        {
            ScreenWelcome.Visibility = Visibility.Collapsed;
            ScreenUpdate.Visibility = Visibility.Collapsed;
            ScreenOptions.Visibility = Visibility.Collapsed;
            ScreenProgress.Visibility = Visibility.Collapsed;
            ScreenCompleted.Visibility = Visibility.Collapsed;
            ScreenUninstall.Visibility = Visibility.Collapsed;

            screenToShow.Visibility = Visibility.Visible;
        }
    }
}
