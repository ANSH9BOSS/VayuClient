using System.Threading.Tasks;
using VayuClient.Services.Account;
using VayuClient.Services.Authentication;
using VayuClient.Services.Download;
using VayuClient.Services.Instance;
using VayuClient.Services.Java;
using VayuClient.Services.Launch;
using VayuClient.Services.Loaders;
using VayuClient.Services.Minecraft;
using VayuClient.Services.Modpack;
using VayuClient.Services.Hardware;
using VayuClient.Services.Monitoring;
using VayuClient.Services.Profiles;
using VayuClient.Services.Settings;
using VayuClient.Services.Server;
using VayuClient.Services.Updates;
using VayuClient.Services.Version;

namespace VayuClient.Core
{
    /// <summary>
    /// Simple service locator for dependency resolution.
    /// Initializes all services in the correct dependency order.
    /// </summary>
    public static class ServiceLocator
    {
        private static readonly Dictionary<Type, object> _services = new();

        public static void Initialize()
        {
            // ─── CRITICAL PATH (fast, no WMI/network) ───────────────────────────
            // These services are needed immediately for the UI to function.
            // Do NOT add WMI queries, filesystem scans, or network calls here.

            var settingsService = new SettingsService();
            Register<ISettingsService>(settingsService);

            var profileService = new ProfileService();
            Register<IProfileService>(profileService);
            var accountService = new AccountService(profileService);
            Register<IAccountService>(accountService);

            var downloadService = new DownloadService();
            Register<IDownloadService>(downloadService);

            var instanceService = new InstanceService();
            Register<IInstanceService>(instanceService);

            var msAuthService = new MicrosoftAuthService();
            Register<IMicrosoftAuthService>(msAuthService);
            Register<IAuthenticationService>(new AuthenticationService(msAuthService, profileService, accountService));

            var versionService = new VersionService();
            Register<IVersionService>(versionService);

            var javaRuntimeService = new JavaRuntimeService();
            Register<IJavaRuntimeService>(javaRuntimeService);

            var minecraftInstaller = new MinecraftInstaller(downloadService, versionService);
            Register<IMinecraftInstaller>(minecraftInstaller);

            var modLoaderInstaller = new ModLoaderInstaller(downloadService);
            Register<IModLoaderInstaller>(modLoaderInstaller);

            var modpackInstaller = new ModpackInstaller(downloadService);
            Register<IModpackInstaller>(modpackInstaller);

            var launchArgumentBuilder = new LaunchArgumentBuilder();
            Register<ILaunchArgumentBuilder>(launchArgumentBuilder);

            // HardwareInfoService: register immediately but defer the WMI query.
            // GetHardwareProfile() is only called when PerformanceService/SettingsVM needs it,
            // so the first real WMI call happens in the background after launch.
            var hardwareInfoService = new HardwareInfoService();
            Register<IHardwareInfoService>(hardwareInfoService);

            var performanceMonitor = new PerformanceMonitorService(hardwareInfoService);
            Register<IPerformanceMonitorService>(performanceMonitor);

            var integrityService = new Services.Integrity.InstanceIntegrityService(
                minecraftInstaller,
                modLoaderInstaller,
                javaRuntimeService,
                versionService);
            Register<Services.Integrity.IInstanceIntegrityService>(integrityService);

            var performanceService = new Services.Performance.PerformanceService(hardwareInfoService);
            Register<Services.Performance.IPerformanceService>(performanceService);

            var launchService = new LaunchService(
                instanceService,
                accountService,
                msAuthService,
                minecraftInstaller,
                javaRuntimeService,
                launchArgumentBuilder,
                modLoaderInstaller,
                modpackInstaller,
                integrityService,
                performanceService);
            Register<ILaunchService>(launchService);

            Register<IMinecraftService>(new MinecraftService());
            Register<IUpdateService>(new UpdateService());

            // Server Manager (Feature 21)
            Register<IServerService>(new ServerService());

            // ─── BACKGROUND PATH (non-blocking) ─────────────────────────────────
            // Discord RPC pipe connection and WMI hardware detection are slow.
            // Initialize them asynchronously after the UI is visible.

            var discordRpc = new Services.Discord.DiscordRpcService();
            Register<Services.Discord.IDiscordRpcService>(discordRpc);

            // Initialize Discord RPC in background — pipe connection takes 100–500ms
            _ = Task.Run(() =>
            {
                try { discordRpc.Initialize(); }
                catch { }
            });

            // Pre-warm hardware detection in background so first access is fast
            _ = Task.Run(() =>
            {
                try { hardwareInfoService.GetHardwareProfile(forceRefresh: false); }
                catch { }
            });
        }

        public static void Register<T>(object instance) where T : class
        {
            _services[typeof(T)] = instance;
        }

        public static T Resolve<T>() where T : class
        {
            if (_services.TryGetValue(typeof(T), out var service))
                return (T)service;

            throw new InvalidOperationException($"Service {typeof(T).Name} not registered.");
        }
    }
}
