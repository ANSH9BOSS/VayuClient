using System;
using System.Reflection;

namespace VayuClientSetup.Services
{
    /// <summary>
    /// Centralized application metadata and runtime semantic versioning information for VayuClient Setup.
    /// </summary>
    public static class SetupAppInfo
    {
        public const string DeveloperName = "ANSH9BOSS";
        public const string AppName = "VayuClient";

        private static readonly Version _assemblyVersion = 
            typeof(SetupAppInfo).Assembly.GetName().Version ?? new Version(1, 0, 1);

        public static Version SemanticVersion => _assemblyVersion;

        public static string VersionString => 
            $"{_assemblyVersion.Major}.{_assemblyVersion.Minor}.{Math.Max(0, _assemblyVersion.Build)}";

        public static string DisplayVersion => $"v{VersionString}";

        public static string FullVersionName => $"VayuClient Setup v{VersionString}";
    }
}
