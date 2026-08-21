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

        private static readonly string _versionString = DetermineVersion();
        private static readonly Version _semanticVersion = DetermineSemanticVersion(_versionString);

        public static Version SemanticVersion => _semanticVersion;

        public static string VersionString => _versionString;

        public static string DisplayVersion => $"v{VersionString}";

        public static string FullVersionName => $"VayuClient Setup v{VersionString}";

        private static string DetermineVersion()
        {
            try
            {
                var infoVer = typeof(SetupAppInfo).Assembly
                    .GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion;
                if (!string.IsNullOrWhiteSpace(infoVer))
                {
                    var plusIdx = infoVer.IndexOf('+');
                    if (plusIdx > 0) infoVer = infoVer.Substring(0, plusIdx);
                    var dashIdx = infoVer.IndexOf('-');
                    if (dashIdx > 0) infoVer = infoVer.Substring(0, dashIdx);
                    infoVer = infoVer.Trim().TrimStart('v', 'V').Trim();
                    if (!string.IsNullOrWhiteSpace(infoVer)) return infoVer;
                }

                var asmVer = typeof(SetupAppInfo).Assembly.GetName().Version;
                if (asmVer != null && (asmVer.Major > 1 || asmVer.Minor > 0 || asmVer.Build > 0))
                {
                    return $"{asmVer.Major}.{asmVer.Minor}.{Math.Max(0, asmVer.Build)}";
                }
            }
            catch { }

            return "1.9.3";
        }

        private static Version DetermineSemanticVersion(string verStr)
        {
            if (Version.TryParse(verStr, out var v)) return v;
            return new Version(1, 9, 3);
        }
    }
}
