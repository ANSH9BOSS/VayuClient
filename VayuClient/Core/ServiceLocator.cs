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
            var hardwareInfoService = new HardwareInfoService();
            Register<IHardwareInfoService>(hardwareInfoService);

            var performanceMonitor = new PerformanceMonitorService(hardwareInfoService);
            Register<IPerformanceMonitorService>(performanceMonitor);
            var settingsService = new SettingsService();
            Register<ISettingsService>(settingsService);

            var downloadService = new DownloadService();
            Register<IDownloadService>(downloadService);

            var javaRuntimeService = new JavaRuntimeService();
            Register<IJavaRuntimeService>(javaRuntimeService);

            var msAuthService = new MicrosoftAuthService();
            Register<IMicrosoftAuthService>(msAuthService);

            var profileService = new ProfileService();
            Register<IProfileService>(profileService);
            var accountService = new AccountService(profileService);
            Register<IAccountService>(accountService);
            Register<IAuthenticationService>(new AuthenticationService(msAuthService, profileService, accountService));
            var versionService = new VersionService();
            Register<IVersionService>(versionService);

            var minecraftInstaller = new MinecraftInstaller(downloadService, versionService);
            Register<IMinecraftInstaller>(minecraftInstaller);

            var modLoaderInstaller = new ModLoaderInstaller(downloadService);
            Register<IModLoaderInstaller>(modLoaderInstaller);

            var modpackInstaller = new ModpackInstaller(downloadService);
            Register<IModpackInstaller>(modpackInstaller);

            var launchArgumentBuilder = new LaunchArgumentBuilder();
            Register<ILaunchArgumentBuilder>(launchArgumentBuilder);

            var instanceService = new InstanceService();
            Register<IInstanceService>(instanceService);

            var launchService = new LaunchService(
                instanceService,
                accountService,
                msAuthService,
                minecraftInstaller,
                javaRuntimeService,
                launchArgumentBuilder,
                modLoaderInstaller,
                modpackInstaller);
            Register<ILaunchService>(launchService);

            Register<IMinecraftService>(new MinecraftService());
            Register<IUpdateService>(new UpdateService());
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
