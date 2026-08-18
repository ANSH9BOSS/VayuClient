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

    /// <summary>
    /// Centralized Microsoft Entra OAuth configuration for desktop authentication.
    /// </summary>
    public static class MicrosoftAuthConfig
    {
        public const string ClientId = "1390dea5-c274-4c1f-8fa6-a9d5fd33c70a";
        public const string MojangClientId = "00000000402b5328";
        public const string Authority = "https://login.microsoftonline.com/common";
        public const string RedirectUri = "http://localhost";
        public static readonly string[] Scopes = { "XboxLive.signin", "offline_access" };

        public const string LiveAuthorizeEndpoint = "https://login.live.com/oauth20_authorize.srf";
        public const string LiveTokenEndpoint = "https://login.live.com/oauth20_token.srf";

        public const string XboxAuthEndpoint = "https://user.auth.xboxlive.com/user/authenticate";
        public const string XstsAuthEndpoint = "https://xsts.auth.xboxlive.com/xsts/authorize";
        public const string MinecraftLoginEndpoint = "https://api.minecraftservices.com/authentication/login_with_xbox";
        public const string MinecraftStoreEndpoint = "https://api.minecraftservices.com/entitlements/mcstore";
        public const string MinecraftProfileEndpoint = "https://api.minecraftservices.com/minecraft/profile";
    }
}
