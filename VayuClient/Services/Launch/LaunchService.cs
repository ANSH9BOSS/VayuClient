using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Account;
using VayuClient.Services.Authentication;
using VayuClient.Services.Instance;
using VayuClient.Services.Java;
using VayuClient.Services.Loaders;
using VayuClient.Services.Minecraft;
using VayuClient.Services.Modpack;

namespace VayuClient.Services.Launch
{
    public class LaunchService : ILaunchService
    {
        private readonly IInstanceService _instanceService;
        private readonly IAccountService _accountService;
        private readonly IMicrosoftAuthService _msAuthService;
        private readonly IMinecraftInstaller _minecraftInstaller;
        private readonly IJavaRuntimeService _javaService;
        private readonly ILaunchArgumentBuilder _argBuilder;
        private readonly IModLoaderInstaller _loaderInstaller;
        private readonly IModpackInstaller _modpackInstaller;

        private Process? _activeGameProcess;
        private readonly string _logsDir;

        public LaunchState CurrentState { get; private set; } = LaunchState.Idle;
        public string StatusMessage { get; private set; } = "Ready";
        public bool IsGameRunning => _activeGameProcess != null && !_activeGameProcess.HasExited;
        public DownloadProgressInfo? CurrentProgress { get; private set; }

        public event Action<LaunchState, string>? StateChanged;
        public event Action<DownloadProgressInfo>? DownloadProgressChanged;

        public LaunchService(
            IInstanceService instanceService,
            IAccountService accountService,
            IMicrosoftAuthService msAuthService,
            IMinecraftInstaller minecraftInstaller,
            IJavaRuntimeService javaService,
            ILaunchArgumentBuilder argBuilder,
            IModLoaderInstaller loaderInstaller,
            IModpackInstaller modpackInstaller)
        {
            _instanceService = instanceService;
            _accountService = accountService;
            _msAuthService = msAuthService;
            _minecraftInstaller = minecraftInstaller;
            _javaService = javaService;
            _argBuilder = argBuilder;
            _loaderInstaller = loaderInstaller;
            _modpackInstaller = modpackInstaller;

            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            _logsDir = Path.Combine(appData, "VayuClient", "logs");
            Directory.CreateDirectory(_logsDir);
        }

        private void SetState(LaunchState state, string message)
        {
            CurrentState = state;
            StatusMessage = message;
            StateChanged?.Invoke(state, message);
        }

        public void KillActiveGame()
        {
            if (_activeGameProcess != null && !_activeGameProcess.HasExited)
            {
                try
                {
                    _activeGameProcess.Kill(entireProcessTree: true);
                }
                catch { }
            }
        }

        public async Task<bool> LaunchInstanceAsync(string? instanceId = null, CancellationToken ct = default)
        {
            if (IsGameRunning)
            {
                SetState(LaunchState.Failed, "A game instance is already running.");
                return false;
            }

            var logFile = Path.Combine(_logsDir, $"launch_{DateTime.Now:yyyyMMdd_HHmmss}.log");
            var logWriter = new StringBuilder();

            void Log(string msg)
            {
                var line = $"[{DateTime.Now:HH:mm:ss.fff}] {msg}";
                logWriter.AppendLine(line);
                try { File.AppendAllText(logFile, line + Environment.NewLine); } catch { }
            }

            try
            {
                SetState(LaunchState.Preparing, "Validating instance and profile...");
                Log("=== VAYUCLIENT MINECRAFT LAUNCH SESSION ===");

                // 1. Resolve Target Instance
                var instance = string.IsNullOrEmpty(instanceId)
                    ? _instanceService.GetActiveInstance()
                    : _instanceService.GetAllInstances().FirstOrDefault(i => i.InstanceId == instanceId);

                if (instance == null)
                {
                    SetState(LaunchState.Failed, "No instance selected. Please create or select an instance first.");
                    Log("ERROR: No instance selected.");
                    return false;
                }

                Log($"Instance: {instance.Name} (ID: {instance.InstanceId})");
                Log($"Minecraft Version: {instance.MinecraftVersion}");
                Log($"Mod Loader: {instance.Loader} ({instance.LoaderVersion ?? "Default"})");
                Log($"Allocated RAM: {instance.RamMB} MB");
                Log($"Game Directory: {instance.GameDirectory}");

                // 2. Resolve Active Profile
                var profile = _accountService.ActiveProfile;
                if (profile == null)
                {
                    SetState(LaunchState.Failed, "No profile selected. Please select or create an offline profile or sign in with Microsoft.");
                    Log("ERROR: No active profile selected.");
                    return false;
                }

                Log($"Profile: {profile.Username} ({profile.AccountType}, UUID: {profile.UUID})");

                // Refresh Microsoft Token if expired
                if (profile.AccountType == AccountType.Microsoft && profile.TokenExpiresAt.HasValue && DateTime.UtcNow >= profile.TokenExpiresAt.Value)
                {
                    SetState(LaunchState.Preparing, "Refreshing Microsoft authentication session...");
                    Log("Refreshing expired Microsoft authentication token...");
                    profile = await _msAuthService.RefreshTokenAsync(profile, ct) ?? profile;
                }

                // 3. Resolve Version Package
                SetState(LaunchState.Preparing, $"Retrieving metadata for Minecraft {instance.MinecraftVersion}...");
                Log($"Fetching Mojang version package for {instance.MinecraftVersion}...");
                var pkg = await _minecraftInstaller.GetVersionPackageAsync(instance.MinecraftVersion, ct);

                // 4. Resolve Java Runtime
                int requiredJava = _javaService.GetRequiredJavaVersion(instance.MinecraftVersion, pkg.JavaVersion?.MajorVersion ?? 0);
                Log($"Required Java Major Version: {requiredJava}");

                var javaRuntime = _javaService.FindCompatibleRuntime(requiredJava);
                if (javaRuntime == null)
                {
                    var msg = $"No compatible Java runtime found. Minecraft {instance.MinecraftVersion} requires 64-bit Java {requiredJava}+.";
                    SetState(LaunchState.Failed, msg);
                    Log($"ERROR: {msg}");
                    return false;
                }

                Log($"Selected Java Runtime: {javaRuntime.DisplayName} at {javaRuntime.Path}");

                // 5. Progress Reporter for Downloads & Installation
                var progressReporter = new Progress<DownloadProgressInfo>(p =>
                {
                    CurrentProgress = p;
                    DownloadProgressChanged?.Invoke(p);
                });

                // 6. Install Minecraft Version (Client, Libraries, Natives, Assets)
                SetState(LaunchState.Downloading, $"Installing Minecraft {instance.MinecraftVersion} files...");
                Log("Starting Minecraft assets, libraries, and natives installation...");

                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var nativesDir = Path.Combine(appData, "VayuClient", "Instances", instance.Name, "natives");
                var sharedAssetsDir = Path.Combine(appData, "VayuClient", "assets");
                Directory.CreateDirectory(nativesDir);
                Directory.CreateDirectory(instance.GameDirectory);

                bool mcInstalled = await _minecraftInstaller.InstallMinecraftAsync(instance.MinecraftVersion, nativesDir, progressReporter, ct);
                if (!mcInstalled)
                {
                    SetState(LaunchState.Failed, "Failed to download and verify Minecraft files.");
                    Log("ERROR: Minecraft installation failed.");
                    return false;
                }

                // 7. Install Mod Loader (Fabric, Quilt, NeoForge, Forge)
                SetState(LaunchState.Installing, $"Configuring {instance.Loader} loader...");
                Log($"Installing mod loader {instance.Loader}...");
                var loaderResult = await _loaderInstaller.InstallLoaderAsync(instance, pkg, progressReporter, ct);
                if (!loaderResult.Success)
                {
                    SetState(LaunchState.Failed, $"Failed to install mod loader: {instance.Loader}");
                    Log($"ERROR: Loader installation failed for {instance.Loader}.");
                    return false;
                }

                // 8. Install Modrinth Modpack if selected
                if (!string.IsNullOrEmpty(instance.ModpackId) && !instance.ModpackId.Equals("none", StringComparison.OrdinalIgnoreCase))
                {
                    SetState(LaunchState.Installing, $"Installing modpack {instance.ModpackId}...");
                    Log($"Installing Modrinth modpack {instance.ModpackId}...");
                    bool modpackOk = await _modpackInstaller.InstallModpackAsync(instance, instance.ModpackId, progressReporter, ct);
                    if (!modpackOk)
                    {
                        SetState(LaunchState.Failed, $"Failed to install modpack: {instance.ModpackId}");
                        Log($"ERROR: Modpack installation failed for {instance.ModpackId}.");
                        return false;
                    }
                }

                // 9. Build Classpath (Deduplicating loader vs vanilla conflicting libraries)
                SetState(LaunchState.Preparing, "Building classpath and launch arguments...");
                Log("Resolving complete classpath with artifact deduplication...");
                var baseClasspath = _minecraftInstaller.ResolveClasspath(pkg, nativesDir);
                var fullClasspath = MergeClasspath(loaderResult.AdditionalLibraries, baseClasspath);

                // 10. Build Launch Arguments
                var launchParams = new LaunchParameters
                {
                    Instance = instance,
                    Profile = profile,
                    VersionPackage = pkg,
                    JavaRuntime = javaRuntime,
                    Classpath = fullClasspath,
                    InstanceNativesDir = nativesDir,
                    SharedAssetsDir = sharedAssetsDir,
                    CustomMainClass = loaderResult.CustomMainClass,
                    AdditionalJvmArgs = loaderResult.AdditionalJvmArgs,
                    AdditionalGameArgs = loaderResult.AdditionalGameArgs
                };

                var argsResult = _argBuilder.BuildArguments(launchParams);
                Log($"Sanitized Launch Command: {argsResult.SanitizedCommandLine}");

                // 11. Launch Game Process
                SetState(LaunchState.Launching, "Launching Minecraft Java Edition...");
                Log("Starting Java process...");

                Directory.CreateDirectory(instance.GameDirectory);
                Directory.CreateDirectory(nativesDir);

                Process? process = null;
                var javaCandidates = new List<string> { argsResult.JavaExecutablePath };

                // Sibling java.exe / javaw.exe
                string dirName = Path.GetDirectoryName(argsResult.JavaExecutablePath) ?? "";
                string exeName = Path.GetFileName(argsResult.JavaExecutablePath);
                if (exeName.Equals("javaw.exe", StringComparison.OrdinalIgnoreCase))
                {
                    javaCandidates.Add(Path.Combine(dirName, "java.exe"));
                }
                else if (exeName.Equals("java.exe", StringComparison.OrdinalIgnoreCase))
                {
                    javaCandidates.Add(Path.Combine(dirName, "javaw.exe"));
                }

                // Add any other compatible installed runtimes (strictly <= 22 to prevent ASM class file version 69 crash)
                var allRuntimes = _javaService.DetectInstalledRuntimes();
                foreach (var rt in allRuntimes.Where(r => r.MajorVersion >= javaRuntime.MajorVersion && r.MajorVersion <= 22))
                {
                    if (!javaCandidates.Contains(rt.Path, StringComparer.OrdinalIgnoreCase))
                    {
                        javaCandidates.Add(rt.Path);
                    }
                }

                Exception? lastLaunchEx = null;
                foreach (var javaPath in javaCandidates)
                {
                    if (!File.Exists(javaPath)) continue;

                    try
                    {
                        var startInfo = new ProcessStartInfo
                        {
                            FileName = javaPath,
                            WorkingDirectory = instance.GameDirectory,
                            RedirectStandardOutput = true,
                            RedirectStandardError = true,
                            UseShellExecute = false,
                            CreateNoWindow = true
                        };

                        foreach (var jvmArg in argsResult.JvmArguments)
                        {
                            startInfo.ArgumentList.Add(jvmArg);
                        }
                        startInfo.ArgumentList.Add(argsResult.MainClass);
                        foreach (var gameArg in argsResult.GameArguments)
                        {
                            startInfo.ArgumentList.Add(gameArg);
                        }

                        var p = new Process { StartInfo = startInfo, EnableRaisingEvents = true };

                        p.OutputDataReceived += (s, e) =>
                        {
                            if (!string.IsNullOrEmpty(e.Data))
                            {
                                Log($"[GAME OUT] {e.Data}");
                            }
                        };

                        p.ErrorDataReceived += (s, e) =>
                        {
                            if (!string.IsNullOrEmpty(e.Data))
                            {
                                Log($"[GAME ERR] {e.Data}");
                            }
                        };

                        if (p.Start())
                        {
                            process = p;
                            Log($"Started Java process successfully with: {javaPath} (PID: {p.Id})");
                            break;
                        }
                    }
                    catch (Exception ex)
                    {
                        lastLaunchEx = ex;
                        Log($"[Launch Attempt]: Failed to start with '{javaPath}': {ex.Message}. Trying fallback candidate...");
                    }
                }

                if (process == null)
                {
                    string errorMsg = lastLaunchEx != null ? lastLaunchEx.Message : "Process failed to start.";
                    SetState(LaunchState.Failed, $"Launch Failed: {errorMsg}");
                    Log($"ERROR: All Java candidates failed to launch. Last error: {errorMsg}");
                    return false;
                }

                _activeGameProcess = process;
                try
                {
                    ServiceLocator.Resolve<Monitoring.IPerformanceMonitorService>().RegisterMinecraftProcess(process);
                }
                catch { }

                try
                {
                    process.BeginOutputReadLine();
                    process.BeginErrorReadLine();
                }
                catch { }

                SetState(LaunchState.Playing, $"Playing Minecraft {instance.MinecraftVersion} ({instance.Name})");
                Log($"Minecraft process running with PID: {process.Id}");

                // 12. Asynchronous Process Lifecycle Monitoring (Non-blocking)
                _ = Task.Run(async () =>
                {
                    try
                    {
                        await process.WaitForExitAsync();
                        int exitCode = process.ExitCode;
                        Log($"Game process exited with ExitCode: {exitCode}");
                        _activeGameProcess = null;
                        try { ServiceLocator.Resolve<Monitoring.IPerformanceMonitorService>().UnregisterMinecraftProcess(); } catch { }

                        if (exitCode == 0)
                        {
                            SetState(LaunchState.GameClosed, "Minecraft closed normally.");
                        }
                        else
                        {
                            SetState(LaunchState.Failed, $"Minecraft exited unexpectedly (Exit Code: {exitCode}). Check logs for details.");
                        }

                        await Task.Delay(3000);
                        if (CurrentState == LaunchState.GameClosed)
                        {
                            SetState(LaunchState.Idle, "Ready");
                        }
                    }
                    catch (Exception ex)
                    {
                        Log($"Error monitoring process: {ex.Message}");
                        _activeGameProcess = null;
                        try { ServiceLocator.Resolve<Monitoring.IPerformanceMonitorService>().UnregisterMinecraftProcess(); } catch { }
                        SetState(LaunchState.Idle, "Ready");
                    }
                });

                return true;
            }
            catch (Exception ex)
            {
                Log($"FATAL LAUNCH EXCEPTION: {ex}");
                SetState(LaunchState.Failed, $"Launch Failed: {ex.Message}");
                return false;
            }
        }

        private static List<string> MergeClasspath(List<string> loaderLibs, List<string> vanillaLibs)
        {
            var merged = new List<string>();
            var seenArtifactKeys = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

            // 1. Add all loader libraries first (they take precedence)
            foreach (var lib in loaderLibs)
            {
                if (string.IsNullOrWhiteSpace(lib)) continue;
                if (!merged.Contains(lib, StringComparer.OrdinalIgnoreCase))
                {
                    merged.Add(lib);
                }
                string key = ExtractArtifactKey(lib);
                if (!string.IsNullOrEmpty(key))
                {
                    seenArtifactKeys.Add(key);
                }
            }

            // 2. Add vanilla libraries, skipping duplicates already provided by loader
            foreach (var lib in vanillaLibs)
            {
                if (string.IsNullOrWhiteSpace(lib)) continue;
                string key = ExtractArtifactKey(lib);
                if (!string.IsNullOrEmpty(key) && seenArtifactKeys.Contains(key))
                {
                    // Skip duplicate/conflicting library (e.g. vanilla asm-9.6 superseded by fabric asm-9.7.1)
                    continue;
                }

                if (!merged.Contains(lib, StringComparer.OrdinalIgnoreCase))
                {
                    merged.Add(lib);
                }
            }

            return merged;
        }

        private static string ExtractArtifactKey(string filePath)
        {
            try
            {
                // filePath e.g. "C:\...\libraries\org\ow2\asm\asm\9.7.1\asm-9.7.1.jar"
                var parts = filePath.Replace('/', '\\').Split(new[] { "\\libraries\\" }, StringSplitOptions.None);
                if (parts.Length > 1)
                {
                    var segments = parts[1].Split('\\', StringSplitOptions.RemoveEmptyEntries);
                    if (segments.Length >= 3)
                    {
                        // segments: [org, ow2, asm, asm, 9.7.1, asm-9.7.1.jar]
                        string artifactName = segments[segments.Length - 3];
                        string group = string.Join('.', segments.Take(segments.Length - 3));
                        return $"{group}:{artifactName}";
                    }
                }

                // Fallback: match filename without version e.g. "asm-9.7.1.jar" -> "asm", "asm-tree-9.7.1.jar" -> "asm-tree"
                string fileName = Path.GetFileNameWithoutExtension(filePath);
                var match = System.Text.RegularExpressions.Regex.Match(fileName, @"^([a-zA-Z0-9_\-\.]+?)(?:-\d+(?:\.\d+)*.*)?$");
                if (match.Success)
                {
                    return match.Groups[1].Value.ToLowerInvariant();
                }
                return fileName.ToLowerInvariant();
            }
            catch
            {
                return Path.GetFileNameWithoutExtension(filePath).ToLowerInvariant();
            }
        }
    }
}
