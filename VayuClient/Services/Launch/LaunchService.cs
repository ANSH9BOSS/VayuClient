using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Account;
using VayuClient.Services.Authentication;
using VayuClient.Services.Instance;
using VayuClient.Services.Integrity;
using VayuClient.Services.Java;
using VayuClient.Services.Loaders;
using VayuClient.Services.Minecraft;
using VayuClient.Services.Modpack;
using VayuClient.Services.Performance;

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
        private readonly IInstanceIntegrityService _integrityService;
        private readonly IPerformanceService _performanceService;
        private readonly IVayuUiCompatibilityValidator _uiValidator;

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
            IModpackInstaller modpackInstaller,
            IInstanceIntegrityService integrityService,
            IPerformanceService performanceService,
            IVayuUiCompatibilityValidator? uiValidator = null)
        {
            _instanceService = instanceService;
            _accountService = accountService;
            _msAuthService = msAuthService;
            _minecraftInstaller = minecraftInstaller;
            _javaService = javaService;
            _argBuilder = argBuilder;
            _loaderInstaller = loaderInstaller;
            _modpackInstaller = modpackInstaller;
            _integrityService = integrityService;
            _performanceService = performanceService;
            _uiValidator = uiValidator ?? new VayuUiCompatibilityValidator();

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
            var logChannel = System.Threading.Channels.Channel.CreateUnbounded<string>(
                new System.Threading.Channels.UnboundedChannelOptions { SingleReader = true, SingleWriter = false });

            // Asynchronous background writer to eliminate stdout pipe stalls
            _ = Task.Run(async () =>
            {
                try
                {
                    using var sw = new StreamWriter(
                        new FileStream(logFile, FileMode.Create, FileAccess.Write, FileShare.ReadWrite, 8192, useAsync: true),
                        Encoding.UTF8);
                    while (await logChannel.Reader.WaitToReadAsync())
                    {
                        while (logChannel.Reader.TryRead(out var item))
                        {
                            await sw.WriteLineAsync(item);
                        }
                        await sw.FlushAsync();
                    }
                }
                catch { }
            });

            void Log(string msg)
            {
                var line = $"[{DateTime.Now:HH:mm:ss.fff}] {msg}";
                logChannel.Writer.TryWrite(line);
                CrashLogger.LogMessage(msg);
            }

            MinecraftInstance? instance = null;
            try
            {
                SetState(LaunchState.Preparing, "Validating instance and profile...");
                Log("=== VAYUCLIENT MINECRAFT LAUNCH SESSION ===");

                // 1. Resolve Target Instance
                instance = string.IsNullOrEmpty(instanceId)
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

                try
                {
                    ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>()?.SetLaunchingPresence(instance.Name, instance.MinecraftVersion, instance.Loader);
                }
                catch { }

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

                if (pkg == null)
                {
                    var msg = $"Failed to retrieve version package for Minecraft {instance.MinecraftVersion}. Launch aborted.";
                    SetState(LaunchState.Failed, msg);
                    Log($"ERROR: {msg}");
                    return false;
                }

                // Strict version invariant check: Resolved package ID must match the instance version
                if (!string.Equals(instance.MinecraftVersion, pkg.Id, StringComparison.OrdinalIgnoreCase))
                {
                    var msg = $"Version package invariant violation! Instance requires Minecraft '{instance.MinecraftVersion}', but resolved package is '{pkg.Id}'. Launch aborted to prevent version mismatch.";
                    SetState(LaunchState.Failed, msg);
                    Log($"FATAL: {msg}");
                    return false;
                }

                // 4. Progress Reporter for Downloads & Installation
                var progressReporter = new Progress<DownloadProgressInfo>(p =>
                {
                    CurrentProgress = p;
                    DownloadProgressChanged?.Invoke(p);
                });

                // 5. Resolve Java Runtime (Auto-Install if missing)
                int requiredJava = _javaService.GetRequiredJavaVersion(instance.MinecraftVersion, pkg.JavaVersion?.MajorVersion ?? 0);
                Log($"Required Java Major Version: {requiredJava}");

                var javaRuntime = _javaService.FindCompatibleRuntime(requiredJava);
                if (javaRuntime == null)
                {
                    SetState(LaunchState.Downloading, $"Java {requiredJava} not found on this computer. Automatically installing standalone OpenJDK Java {requiredJava}...");
                    Log($"Java {requiredJava} not found. Initiating automated Java runtime download and installation...");

                    try
                    {
                        javaRuntime = await _javaService.EnsureJavaRuntimeAsync(requiredJava, progressReporter, ct);
                    }
                    catch (Exception ex)
                    {
                        Log($"ERROR during automatic Java installation: {ex.Message}");
                    }
                }

                if (javaRuntime == null)
                {
                    var msg = $"No compatible Java runtime found and automated installation failed. Minecraft {instance.MinecraftVersion} requires 64-bit Java {requiredJava}+.";
                    SetState(LaunchState.Failed, msg);
                    Log($"ERROR: {msg}");
                    return false;
                }

                Log($"Selected Java Runtime: {javaRuntime.DisplayName} at {javaRuntime.Path}");

                // 6. Install Minecraft Version (Client, Libraries, Natives, Assets)
                SetState(LaunchState.Downloading, $"Installing Minecraft {instance.MinecraftVersion} files...");
                Log("Starting Minecraft assets, libraries, and natives installation...");

                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                var instanceBaseDir = !string.IsNullOrEmpty(instance.GameDirectory) && Directory.Exists(instance.GameDirectory)
                    ? (Directory.GetParent(instance.GameDirectory)?.FullName ?? instance.GameDirectory)
                    : Path.Combine(appData, "VayuClient", "Instances", instance.Name);
                var nativesDir = Path.Combine(instanceBaseDir, "natives");
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

                // 8.5 Sanitize Mods Directory (Purge duplicate mod IDs and loader caches)
                SanitizeInstanceMods(instance.GameDirectory, instance.MinecraftVersion, Log);

                // 8.6 Pre-Launch Version & Artifact Integrity Check
                SetState(LaunchState.Preparing, "Validating installation integrity...");
                Log($"[InstanceValidation] Validating {instance.Name} (Minecraft: {instance.MinecraftVersion}, Loader: {instance.Loader})...");
                var integrity = await _integrityService.ValidateIntegrityAsync(instance, ct);

                Log("[InstanceValidation] Result: " + (integrity.IsValid ? "PASS" : "FAIL"));
                Log($"[Launch] Minecraft version: {instance.MinecraftVersion}");
                Log($"[Launch] Loader: {instance.Loader} {instance.LoaderVersion ?? "Default"}");

                if (!integrity.IsValid)
                {
                    var errors = string.Join("; ", integrity.Errors);
                    Log($"[InstanceValidation] ERROR: Pre-launch integrity check failed: {errors}");
                    SetState(LaunchState.Failed, $"Launch blocked: instance configuration mismatch - {errors}.");
                    return false;
                }

                // 8.7 Apply Instance Performance Settings (options.txt and mod configs)
                await _performanceService.ApplyInstanceOptionsAsync(instance, _performanceService.CurrentSettings);

                // 8.8 Auto-deploy and validate version-compatible VayuClient In-Game UI Mod
                EnsureVayuClientUiMod(instance, javaRuntime, Log);

                // 9. Build Classpath (Deduplicating loader vs vanilla conflicting libraries)
                SetState(LaunchState.Preparing, "Building classpath and launch arguments...");
                Log("Resolving complete classpath with artifact deduplication...");
                var baseClasspath = _minecraftInstaller.ResolveClasspath(pkg, nativesDir);
                var fullClasspath = MergeClasspath(loaderResult.AdditionalLibraries, baseClasspath);

                var combinedJvmArgs = new List<string>();
                if (loaderResult.AdditionalJvmArgs != null) combinedJvmArgs.AddRange(loaderResult.AdditionalJvmArgs);
                if (!string.IsNullOrWhiteSpace(instance.JvmArguments))
                {
                    var splitArgs = instance.JvmArguments.Split(new[] { ' ', '\t', '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
                    combinedJvmArgs.AddRange(splitArgs);
                }

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
                    AdditionalJvmArgs = combinedJvmArgs,
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

                // Add any other compatible installed runtimes
                var allRuntimes = _javaService.DetectInstalledRuntimes();
                foreach (var rt in allRuntimes.Where(r => r.MajorVersion >= javaRuntime.MajorVersion))
                {
                    if (!javaCandidates.Contains(rt.Path, StringComparer.OrdinalIgnoreCase))
                    {
                        javaCandidates.Add(rt.Path);
                    }
                }

                // Build JVM and game command line with safe Windows quoting
                var sbArgs = new StringBuilder();
                foreach (var jvmArg in argsResult.JvmArguments)
                {
                    if (string.IsNullOrWhiteSpace(jvmArg)) continue;
                    if (jvmArg.StartsWith("-D", StringComparison.OrdinalIgnoreCase) && jvmArg.Contains('='))
                    {
                        int eqIndex = jvmArg.IndexOf('=');
                        string key = jvmArg.Substring(0, eqIndex);
                        string val = jvmArg.Substring(eqIndex + 1);
                        if (val.StartsWith("\"") && val.EndsWith("\""))
                        {
                            sbArgs.Append($"{key}={val} ");
                        }
                        else if (val.Contains(' '))
                        {
                            sbArgs.Append($"{key}=\"{val}\" ");
                        }
                        else
                        {
                            sbArgs.Append($"{key}={val} ");
                        }
                    }
                    else if (jvmArg == "-cp" || jvmArg == "-classpath")
                    {
                        sbArgs.Append($"{jvmArg} ");
                    }
                    else
                    {
                        if (jvmArg.Contains(' ') && !jvmArg.StartsWith("\""))
                        {
                            sbArgs.Append($"\"{jvmArg}\" ");
                        }
                        else
                        {
                            sbArgs.Append($"{jvmArg} ");
                        }
                    }
                }

                sbArgs.Append($"{argsResult.MainClass} ");

                foreach (var gameArg in argsResult.GameArguments)
                {
                    if (string.IsNullOrWhiteSpace(gameArg)) continue;
                    if (gameArg.Contains(' ') && !gameArg.StartsWith("\""))
                    {
                        sbArgs.Append($"\"{gameArg}\" ");
                    }
                    else
                    {
                        sbArgs.Append($"{gameArg} ");
                    }
                }

                string finalArguments = sbArgs.ToString().TrimEnd();

                Exception? lastLaunchEx = null;
                foreach (var javaPath in javaCandidates)
                {
                    if (!File.Exists(javaPath)) continue;

                    try
                    {
                        var startInfo = new ProcessStartInfo
                        {
                            FileName = javaPath,
                            Arguments = finalArguments,
                            WorkingDirectory = instance.GameDirectory,
                            RedirectStandardOutput = true,
                            RedirectStandardError = true,
                            UseShellExecute = false,
                            CreateNoWindow = true
                        };

                        // Enforce dedicated GPU execution on Windows dual-GPU systems (NVIDIA RTX 5050 / AMD Radeon)
                        try
                        {
                            startInfo.EnvironmentVariables["__NV_PRIME_RENDER_OFFLOAD"] = "1";
                            startInfo.EnvironmentVariables["__GLX_VENDOR_LIBRARY_NAME"] = "nvidia";
                            startInfo.EnvironmentVariables["SHIM_MCCOMPAT"] = "0x800000001";
                            startInfo.EnvironmentVariables["CUDA_VISIBLE_DEVICES"] = "0";
                            startInfo.EnvironmentVariables["GPU_DEVICE_ORDINAL"] = "0";

                            // Set Windows DirectX high-performance GPU preference for this javaw executable
                            using var key = Microsoft.Win32.Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\DirectX\UserGpuPreferences");
                            if (key != null)
                            {
                                key.SetValue(javaPath, "GpuPreference=2;");
                            }
                        }
                        catch { }

                        var p = new Process { StartInfo = startInfo, EnableRaisingEvents = true };

                        p.OutputDataReceived += (s, e) =>
                        {
                            if (!string.IsNullOrEmpty(e.Data))
                            {
                                logChannel.Writer.TryWrite($"[{DateTime.Now:HH:mm:ss.fff}] [GAME OUT] {e.Data}");
                                if (e.Data.Contains("ERROR", StringComparison.OrdinalIgnoreCase) || 
                                    e.Data.Contains("FATAL", StringComparison.OrdinalIgnoreCase) ||
                                    e.Data.Contains("Exception", StringComparison.OrdinalIgnoreCase))
                                {
                                    CrashLogger.LogMessage($"[GAME ERROR] {e.Data}");
                                }
                            }
                        };

                        p.ErrorDataReceived += (s, e) =>
                        {
                            if (!string.IsNullOrEmpty(e.Data))
                            {
                                logChannel.Writer.TryWrite($"[{DateTime.Now:HH:mm:ss.fff}] [GAME ERR] {e.Data}");
                                CrashLogger.LogMessage($"[GAME ERR] {e.Data}");
                            }
                        };

                        if (p.Start())
                        {
                            process = p;
                            Log($"Started Java process successfully on high-performance GPU with: {javaPath} (PID: {p.Id})");
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
                    try { ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>()?.SetInLauncherPresence(instance.Name, instance.MinecraftVersion, instance.Loader); } catch { }
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

                try
                {
                    var discordRpc = ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>();
                    discordRpc?.SetInGamePresence(instance.Name, instance.MinecraftVersion, instance.Loader);
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
                        try { ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>()?.SetInLauncherPresence(instance.Name, instance.MinecraftVersion, instance.Loader); } catch { }

                        if (exitCode == 0)
                        {
                            SetState(LaunchState.GameClosed, "Minecraft closed normally.");
                        }
                        else
                        {
                            string crashMsg = $"Minecraft exited unexpectedly (Exit Code: {exitCode}).";
                            SetState(LaunchState.Failed, crashMsg);

                            // Instant Crash Log Shower
                            try
                            {
                                var (details, logPath) = CrashLogger.GetCrashDetails(instance.GameDirectory, crashMsg);
                                Views.ErrorDialog.ShowDialogSafe(
                                    summary: $"Minecraft instance '{instance.Name}' ({instance.MinecraftVersion} {instance.Loader}) crashed with Exit Code {exitCode}.",
                                    details: details,
                                    logFilePath: logPath,
                                    header: $"Minecraft Crashed (Exit Code: {exitCode})");
                            }
                            catch { }
                        }

                        await Task.Delay(2500);
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
                        try { ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>()?.SetInLauncherPresence(instance.Name, instance.MinecraftVersion, instance.Loader); } catch { }
                        SetState(LaunchState.Idle, "Ready");
                    }
                });

                return true;
            }
            catch (Exception ex)
            {
                Log($"FATAL LAUNCH EXCEPTION: {ex}");
                string errorMsg = $"Launch Failed: {ex.Message}";
                SetState(LaunchState.Failed, errorMsg);
                try { ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>()?.SetInLauncherPresence(instance?.Name, instance?.MinecraftVersion, instance?.Loader); } catch { }

                try
                {
                    var (details, logPath) = CrashLogger.GetCrashDetails(instance?.GameDirectory, errorMsg);
                    Views.ErrorDialog.ShowDialogSafe(
                        summary: $"Failed to start instance '{instance?.Name ?? "Minecraft"}': {ex.Message}",
                        details: $"{ex.GetType().FullName}: {ex.Message}\n\nStack Trace:\n{ex.StackTrace}\n\nRecent Logs:\n{details}",
                        logFilePath: logPath,
                        header: "Launch Execution Failed");
                }
                catch { }

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

        private static void SanitizeInstanceMods(string gameDir, string targetMinecraftVersion, Action<string> log)
        {
            try
            {
                var modsDir = Path.Combine(gameDir, "mods");
                if (!Directory.Exists(modsDir)) return;

                // 1. Remove internal loader processed cache directories if leaked into mods/
                var processedDir = Path.Combine(modsDir, "processedMods");
                if (Directory.Exists(processedDir))
                {
                    try
                    {
                        Directory.Delete(processedDir, true);
                        log("Cleaned internal processedMods cache folder from mods directory.");
                    }
                    catch { }
                }

                var jars = Directory.GetFiles(modsDir, "*.jar", SearchOption.TopDirectoryOnly);
                if (jars.Length == 0) return;

                var seenModIds = new Dictionary<string, (string FilePath, string Version, string TargetMc)>(StringComparer.OrdinalIgnoreCase);

                foreach (var jarPath in jars)
                {
                    try
                    {
                        using var zip = ZipFile.OpenRead(jarPath);
                        var modJsonEntry = zip.GetEntry("fabric.mod.json");
                        if (modJsonEntry != null)
                        {
                            using var stream = modJsonEntry.Open();
                            using var reader = new StreamReader(stream);
                            var jsonText = reader.ReadToEnd();
                            var jsonObj = JObject.Parse(jsonText);
                            var modId = jsonObj["id"]?.ToString();
                            var modVersion = jsonObj["version"]?.ToString() ?? "1.0.0";
                            var depends = jsonObj["depends"] as JObject;
                            var mcDep = depends?["minecraft"]?.ToString() ?? "";

                            if (!string.IsNullOrEmpty(modId))
                            {
                                if (seenModIds.TryGetValue(modId, out var existing))
                                {
                                    bool isCurrentMatch = string.IsNullOrEmpty(mcDep) || mcDep.Contains(targetMinecraftVersion) || mcDep == "*";
                                    bool isExistingMatch = string.IsNullOrEmpty(existing.TargetMc) || existing.TargetMc.Contains(targetMinecraftVersion) || existing.TargetMc == "*";

                                    if (isCurrentMatch && !isExistingMatch)
                                    {
                                        DisableMod(existing.FilePath, $"Duplicate mod ID '{modId}' incompatible with MC {targetMinecraftVersion}", log);
                                        seenModIds[modId] = (jarPath, modVersion, mcDep);
                                    }
                                    else
                                    {
                                        DisableMod(jarPath, $"Duplicate mod ID '{modId}' (retaining {Path.GetFileName(existing.FilePath)})", log);
                                    }
                                    continue;
                                }

                                if (!string.IsNullOrEmpty(mcDep) && mcDep != "*" && !string.IsNullOrEmpty(targetMinecraftVersion))
                                {
                                    if ((mcDep.StartsWith("1.21") || mcDep.Contains("1.21.4")) && targetMinecraftVersion.StartsWith("26."))
                                    {
                                        DisableMod(jarPath, $"Requires Minecraft {mcDep}, incompatible with target {targetMinecraftVersion}", log);
                                        continue;
                                    }
                                }

                                seenModIds[modId] = (jarPath, modVersion, mcDep);
                            }
                        }
                    }
                    catch
                    {
                        // Ignore unreadable or native binary jars
                    }
                }
            }
            catch (Exception ex)
            {
                log($"[ModSanitizer] Notice: {ex.Message}");
            }
        }

        private static void DisableMod(string jarPath, string reason, Action<string> log)
        {
            try
            {
                var disabledPath = jarPath + ".disabled";
                if (File.Exists(disabledPath)) File.Delete(disabledPath);
                File.Move(jarPath, disabledPath);
                log($"[ModSanitizer] Automatically disabled conflicting mod: {Path.GetFileName(jarPath)} ({reason})");
            }
            catch (Exception ex)
            {
                log($"[ModSanitizer] Failed to disable {Path.GetFileName(jarPath)}: {ex.Message}");
            }
        }

        private void EnsureVayuClientUiMod(MinecraftInstance instance, JavaRuntimeInfo javaRuntime, Action<string>? log = null)
        {
            try
            {
                var modsDir = Path.Combine(instance.GameDirectory, "mods");
                Directory.CreateDirectory(modsDir);

                // 1. Purge any stale or incompatible UI mod from instance directory
                _uiValidator.PurgeIncompatibleUiMods(modsDir, javaRuntime.MajorVersion, instance.MinecraftVersion);

                // 2. Resolve source candidate
                var appBase = AppDomain.CurrentDomain.BaseDirectory;
                var sourceMod = Path.Combine(appBase, "Assets", "Mods", "vayuclient-ui-1.6.0.jar");
                if (!File.Exists(sourceMod))
                {
                    var altSource = Path.Combine(appBase, "..", "..", "..", "Assets", "Mods", "vayuclient-ui-1.6.0.jar");
                    if (File.Exists(altSource)) sourceMod = Path.GetFullPath(altSource);
                }
                if (!File.Exists(sourceMod))
                {
                    var distSource = Path.Combine(appBase, "vayuclient-ui-1.6.0.jar");
                    if (File.Exists(distSource)) sourceMod = distSource;
                }

                if (!File.Exists(sourceMod))
                {
                    log?.Invoke($"[VayuUI] Notice: VayuClient UI artifact not found at {sourceMod}, launching in standard mode.");
                    CrashLogger.LogMessage($"[VayuUI] Notice: VayuClient UI artifact not found at {sourceMod}");
                    return;
                }

                // 3. Pre-launch Bytecode Compatibility Validation Gate
                if (!_uiValidator.ValidateCompatibility(javaRuntime.MajorVersion, sourceMod, instance.MinecraftVersion, out string failureReason))
                {
                    log?.Invoke($"[VayuUI] CRITICAL COMPATIBILITY ERROR: {failureReason}");
                    CrashLogger.LogMessage($"[VayuUI] CRITICAL COMPATIBILITY ERROR: {failureReason}");
                    throw new InvalidOperationException(failureReason);
                }

                // 4. Deploy and assert integrity
                var dest = Path.Combine(modsDir, "vayuclient-ui-1.6.0.jar");
                File.Copy(sourceMod, dest, true);

                var destInfo = _uiValidator.InspectArtifact(dest);
                if (!destInfo.IsValid)
                {
                    throw new InvalidOperationException($"Deployed VayuClient UI artifact is invalid: {destInfo.ErrorMessage}");
                }

                log?.Invoke($"[VayuUI] Deployed and verified VayuClient UI mod (Java {javaRuntime.MajorVersion} / Bytecode {destInfo.BytecodeMajor}) to {dest}");
                CrashLogger.LogMessage($"[VayuUI] Deployed VayuClient In-Game UI mod (Java {javaRuntime.MajorVersion} / Bytecode {destInfo.BytecodeMajor}) to {dest}");
            }
            catch (InvalidOperationException)
            {
                throw;
            }
            catch (Exception ex)
            {
                log?.Invoke($"[VayuUI] Warning: in-game UI deployment exception: {ex.Message}");
                CrashLogger.LogMessage($"[VayuUI] Warning: could not deploy in-game UI mod: {ex.Message}");
            }
        }
    }
}
