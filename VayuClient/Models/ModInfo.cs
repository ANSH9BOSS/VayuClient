using CommunityToolkit.Mvvm.ComponentModel;

namespace VayuClient.Models
{
    /// <summary>
    /// Represents mod metadata.
    /// </summary>
    public partial class ModInfo : ObservableObject
    {
        public string Id { get; set; } = string.Empty;
        public string Name { get; set; } = string.Empty;
        public string Version { get; set; } = string.Empty;
        public string MinecraftVersion { get; set; } = "1.21.11";
        public string Author { get; set; } = string.Empty;
        public string Description { get; set; } = string.Empty;
        public string Category { get; set; } = "UTILITY"; // PERFORMANCE, PVP, HUD, UTILITY, VISUAL, WORLD, LIBRARY
        public string FileName { get; set; } = string.Empty;
        public string FilePath { get; set; } = string.Empty;
        public string FileSizeFormatted { get; set; } = string.Empty;

        [ObservableProperty]
        private bool _isEnabled = true;

        [ObservableProperty]
        private string? _iconPath;

        public string ModLoader { get; set; } = "Fabric";

        public string StatusText => IsEnabled ? "ENABLED" : "DISABLED";
        public string StatusColor => IsEnabled ? "#10B981" : "#64748B";

        public string CategoryBadgeColor => (Category ?? "UTILITY").ToUpperInvariant() switch
        {
            "PERFORMANCE" => "#00D2FF",
            "PVP" => "#EF4444",
            "HUD" => "#F59E0B",
            "VISUAL" => "#A855F7",
            "WORLD" => "#10B981",
            "LIBRARY" => "#64748B",
            _ => "#38BDF8"
        };
    }
}
