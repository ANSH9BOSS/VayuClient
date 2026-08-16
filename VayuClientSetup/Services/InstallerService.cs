using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using Microsoft.Win32;

namespace VayuClientSetup.Services
{
    public class ExistingInstallInfo
    {
        public bool Exists { get; set; }
        public string InstallPath { get; set; } = string.Empty;
        public string Version { get; set; } = "1.0.0";
        public bool IsRunning { get; set; }
    }

    public class InstallerService
    {
        public static string CurrentSetupVersion => SetupAppInfo.VersionString;

        [DllImport("shell32.dll", CharSet = CharSet.Auto, SetLastError = true)]
        private static extern void SHChangeNotify(int wEventId, uint uFlags, IntPtr dwItem1, IntPtr dwItem2);

        private const int SHCNE_ASSOCCHANGED = 0x08000000;
        private const uint SHCNF_IDLIST = 0x0000;

        public static string GetDefaultInstallPath()
        {
            var localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(localAppData, "Programs", "VayuClient");
        }

        public static string GetProgramFilesInstallPath()
        {
            var programFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
            return Path.Combine(programFiles, "VayuClient");
        }

        public static ExistingInstallInfo DetectExistingInstallation()
        {
            var info = new ExistingInstallInfo();

            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\VayuClient");
                if (key != null)
                {
                    var installLoc = key.GetValue("InstallLocation") as string;
                    var version = key.GetValue("DisplayVersion") as string;

                    if (!string.IsNullOrEmpty(installLoc) && Directory.Exists(installLoc) && File.Exists(Path.Combine(installLoc, "VayuClient.exe")))
                    {
                        info.Exists = true;
                        info.InstallPath = installLoc;
                        info.Version = !string.IsNullOrEmpty(version) ? version : "1.0.0";
                    }
                }
            }
            catch { }

            if (!info.Exists)
            {
                var defaultPath = GetDefaultInstallPath();
                var defaultExe = Path.Combine(defaultPath, "VayuClient.exe");
                if (File.Exists(defaultExe))
                {
                    info.Exists = true;
                    info.InstallPath = defaultPath;
                    try
                    {
                        var vi = FileVersionInfo.GetVersionInfo(defaultExe);
                        info.Version = !string.IsNullOrEmpty(vi.FileVersion) ? vi.FileVersion : "1.0.0";
                    }
                    catch
                    {
                        info.Version = "1.0.0";
                    }
                }
            }

            info.IsRunning = Process.GetProcessesByName("VayuClient").Length > 0;
            return info;
        }

        public static async Task<bool> CloseRunningProcessesAsync(int timeoutMs = 3000)
        {
            var processes = Process.GetProcessesByName("VayuClient");
            if (processes.Length == 0) return true;

            foreach (var proc in processes)
            {
                try
                {
                    proc.CloseMainWindow();
                }
                catch { }
            }

            var sw = Stopwatch.StartNew();
            while (sw.ElapsedMilliseconds < timeoutMs)
            {
                if (Process.GetProcessesByName("VayuClient").Length == 0)
                {
                    return true;
                }
                await Task.Delay(100);
            }

            // Force kill remaining VayuClient processes
            foreach (var proc in Process.GetProcessesByName("VayuClient"))
            {
                try
                {
                    proc.Kill();
                    proc.WaitForExit(1000);
                }
                catch { }
            }

            return Process.GetProcessesByName("VayuClient").Length == 0;
        }

        public static void DeleteOldShortcuts()
        {
            try
            {
                var desktopShortcut = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), "VayuClient.lnk");
                if (File.Exists(desktopShortcut)) File.Delete(desktopShortcut);

                var startMenuDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "VayuClient");
                if (Directory.Exists(startMenuDir)) Directory.Delete(startMenuDir, true);
            }
            catch { }
        }

        public async Task InstallOrUpdateAsync(
            string targetDir,
            bool createDesktopShortcut,
            bool createStartMenuShortcut,
            Action<int, string> progressCallback)
        {
            progressCallback(5, "Preparing installation environment...");
            await Task.Delay(200);

            // Close running instances of VayuClient
            await CloseRunningProcessesAsync(3000);

            Directory.CreateDirectory(targetDir);

            // Clean old binary files in target installation directory if updating
            try
            {
                var currentExe = Environment.ProcessPath ?? string.Empty;
                foreach (var file in Directory.GetFiles(targetDir))
                {
                    if (!string.Equals(file, currentExe, StringComparison.OrdinalIgnoreCase))
                    {
                        try { File.Delete(file); } catch { }
                    }
                }
            }
            catch { }

            // Copy self (installer) into target directory as uninstaller payload
            var currentExePath = Environment.ProcessPath ?? AppContext.BaseDirectory;
            var installedSetupPath = Path.Combine(targetDir, "VayuClientSetup.exe");
            if (File.Exists(currentExePath) && !string.Equals(currentExePath, installedSetupPath, StringComparison.OrdinalIgnoreCase))
            {
                try { File.Copy(currentExePath, installedSetupPath, true); } catch { }
            }

            progressCallback(20, "Extracting VayuClient application files...");
            await Task.Delay(200);

            // Extract payload zip embedded resource
            var assembly = Assembly.GetExecutingAssembly();
            var resourceName = assembly.GetManifestResourceNames().FirstOrDefault(r => r.EndsWith("VayuClientPayload.zip"));

            if (resourceName != null)
            {
                using var stream = assembly.GetManifestResourceStream(resourceName);
                if (stream != null)
                {
                    using var archive = new ZipArchive(stream, ZipArchiveMode.Read);
                    int totalEntries = archive.Entries.Count;
                    int extracted = 0;

                    foreach (var entry in archive.Entries)
                    {
                        var destPath = Path.Combine(targetDir, entry.FullName);
                        if (string.IsNullOrEmpty(entry.Name))
                        {
                            Directory.CreateDirectory(destPath);
                        }
                        else
                        {
                            var parent = Path.GetDirectoryName(destPath);
                            if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                            entry.ExtractToFile(destPath, overwrite: true);
                        }

                        extracted++;
                        int percent = 20 + (int)((extracted / (double)totalEntries) * 50);
                        progressCallback(percent, $"Extracting {entry.Name}...");
                        await Task.Delay(5);
                    }
                }
            }

            // Ensure root vayu_logo.ico exists in target installation directory
            var rootIco = Path.Combine(targetDir, "vayu_logo.ico");
            var subIco = Path.Combine(targetDir, "Assets", "Images", "vayu_logo.ico");
            if (!File.Exists(rootIco) && File.Exists(subIco))
            {
                try { File.Copy(subIco, rootIco, true); } catch { }
            }
            else if (!File.Exists(subIco) && File.Exists(rootIco))
            {
                var subDir = Path.GetDirectoryName(subIco);
                if (!string.IsNullOrEmpty(subDir)) Directory.CreateDirectory(subDir);
                try { File.Copy(rootIco, subIco, true); } catch { }
            }

            progressCallback(75, "Recreating shortcuts and branding...");
            await Task.Delay(200);

            // Delete old shortcuts first to flush Windows caching
            DeleteOldShortcuts();

            var installedExePath = Path.Combine(targetDir, "VayuClient.exe");
            string iconLocation = File.Exists(rootIco) ? rootIco : $"{installedExePath},0";

            if (createDesktopShortcut)
            {
                var desktopPath = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                var shortcutPath = Path.Combine(desktopPath, "VayuClient.lnk");
                CreateShortcut(shortcutPath, installedExePath, targetDir, "VayuClient — Premium Minecraft Launcher by ANSH9BOSS", iconLocation);
            }

            if (createStartMenuShortcut)
            {
                var startMenuPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "VayuClient");
                Directory.CreateDirectory(startMenuPath);
                var shortcutPath = Path.Combine(startMenuPath, "VayuClient.lnk");
                CreateShortcut(shortcutPath, installedExePath, targetDir, "VayuClient — Premium Minecraft Launcher by ANSH9BOSS", iconLocation);
            }

            progressCallback(90, "Registering Windows application...");
            await Task.Delay(200);

            RegisterWindowsApp(targetDir, installedExePath, installedSetupPath, iconLocation);

            // Notify Windows shell to flush icon cache
            try
            {
                SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, IntPtr.Zero, IntPtr.Zero);
            }
            catch { }

            progressCallback(100, "Finalizing installation...");
            await Task.Delay(200);
        }

        public static void RegisterWindowsApp(string targetDir, string installedExePath, string installedSetupPath, string? iconLocation = null)
        {
            try
            {
                using var key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\VayuClient");
                if (key != null)
                {
                    key.SetValue("DisplayName", "VayuClient");
                    key.SetValue("Publisher", "ANSH9BOSS");
                    key.SetValue("DisplayVersion", CurrentSetupVersion);
                    key.SetValue("DisplayIcon", iconLocation ?? $"{installedExePath},0");
                    key.SetValue("InstallLocation", targetDir);
                    key.SetValue("UninstallString", $"\"{installedSetupPath}\" /uninstall");
                    key.SetValue("NoModify", 1);
                    key.SetValue("NoRepair", 1);
                }
            }
            catch { }
        }

        public async Task UninstallAsync(Action<int, string> progressCallback)
        {
            progressCallback(10, "Preparing uninstallation...");
            await Task.Delay(200);

            await CloseRunningProcessesAsync(3000);

            // Get install location from registry
            string? installDir = null;
            try
            {
                using var key = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\VayuClient");
                installDir = key?.GetValue("InstallLocation") as string;
            }
            catch { }

            if (string.IsNullOrEmpty(installDir) || !Directory.Exists(installDir))
            {
                installDir = GetDefaultInstallPath();
            }

            progressCallback(30, "Removing Desktop and Start Menu shortcuts...");
            await Task.Delay(200);

            DeleteOldShortcuts();

            progressCallback(60, "Removing Windows registry registration...");
            await Task.Delay(200);

            try
            {
                Registry.CurrentUser.DeleteSubKeyTree(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\VayuClient", false);
            }
            catch { }

            // Notify Windows shell
            try
            {
                SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST, IntPtr.Zero, IntPtr.Zero);
            }
            catch { }

            progressCallback(80, "Removing application files...");
            await Task.Delay(300);

            // Remove application files while explicitly preserving APPDATA user data
            if (Directory.Exists(installDir))
            {
                var currentExe = Environment.ProcessPath ?? string.Empty;
                foreach (var file in Directory.GetFiles(installDir))
                {
                    if (!string.Equals(file, currentExe, StringComparison.OrdinalIgnoreCase))
                    {
                        try { File.Delete(file); } catch { }
                    }
                }
            }

            progressCallback(100, "VayuClient has been cleanly uninstalled.");
            await Task.Delay(300);
        }

        public static void CreateShortcut(string shortcutPath, string targetExe, string workingDir, string description, string? iconLocation = null)
        {
            try
            {
                Type? shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType != null)
                {
                    dynamic shell = Activator.CreateInstance(shellType)!;
                    var shortcut = shell.CreateShortcut(shortcutPath);
                    shortcut.TargetPath = targetExe;
                    shortcut.WorkingDirectory = workingDir;
                    shortcut.Description = description;
                    if (!string.IsNullOrEmpty(iconLocation))
                    {
                        shortcut.IconLocation = iconLocation;
                    }
                    else
                    {
                        shortcut.IconLocation = $"{targetExe},0";
                    }
                    shortcut.Save();
                }
            }
            catch { }
        }
    }
}
