using System;

namespace VayuClient.Models
{
    public class LauncherSettings
    {
        // Appearance
        public string Theme { get; set; } = "Dark";
        public string AccentColor { get; set; } = "#2563EB";
        public string Language { get; set; } = "🇺🇸 English";
        public bool SmoothAnimations { get; set; } = true;

        // Interface
        public string InstallationsSorting { get; set; } = "Sort installations by name";
        public bool HideDefaultInstallations { get; set; } = false;
        public bool ShowLauncherConsole { get; set; } = false;

        // Performance & Java
        public string PerformanceMode { get; set; } = "Balanced";
        public int DownloadConcurrency { get; set; } = 8;
        public int DefaultMemoryMB { get; set; } = 4096;
        public string? CustomJavaPath { get; set; } = null;
        public bool NativeTitleBar { get; set; } = false;
        public bool AllowXmxArguments { get; set; } = false;
        public bool UseDedicatedGpu { get; set; } = true;

        // Game & Launcher Behavior
        public bool CloseLauncherOnGameStart { get; set; } = false;
        public bool MinimizeOnLaunch { get; set; } = false;
        public bool DiscordRichPresence { get; set; } = true;
        public bool DnsOverride { get; set; } = true;
        public bool ForceLanOfflineMode { get; set; } = false;
        public bool ModernForgeInstaller { get; set; } = true;
    }
}
