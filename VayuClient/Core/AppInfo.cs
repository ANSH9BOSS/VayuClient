using System;
using System.Reflection;

namespace VayuClient.Core
{
    /// <summary>
    /// Centralized application metadata and runtime semantic versioning information.
    /// Reads assembly version attributes generated from Directory.Build.props.
    /// </summary>
    public static class AppInfo
    {
        public const string DeveloperName = "ANSH9BOSS";
        public const string AppName = "VayuClient";

        private static readonly string _versionString = DetermineVersion();
        private static readonly Version _semanticVersion = DetermineSemanticVersion(_versionString);

        public static Version SemanticVersion => _semanticVersion;

        public static string VersionString => _versionString;

        public static string DisplayVersion => $"v{VersionString}";

        public static string FullVersionName => $"VayuClient v{VersionString}";

        public static string UserAgent => $"VayuClient/{VersionString} ({DeveloperName})";

        private static string DetermineVersion()
        {
            try
            {
                var infoVer = typeof(AppInfo).Assembly
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

                var asmVer = typeof(AppInfo).Assembly.GetName().Version;
                if (asmVer != null && (asmVer.Major > 1 || asmVer.Minor > 0 || asmVer.Build > 0))
                {
                    return $"{asmVer.Major}.{asmVer.Minor}.{Math.Max(0, asmVer.Build)}";
                }
            }
            catch { }

            return "1.5.1";
        }

        private static Version DetermineSemanticVersion(string verStr)
        {
            if (Version.TryParse(verStr, out var v)) return v;
            return new Version(1, 5, 1);
        }
    }
}
