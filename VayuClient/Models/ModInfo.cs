using CommunityToolkit.Mvvm.ComponentModel;

namespace VayuClient.Models
{
    public enum ModCompatibilityState
    {
        Compatible,
        Incompatible,
        UpdateAvailable,
        UnsupportedLoader,
        UnsupportedMinecraftVersion
    }

    /// <summary>
    /// Represents mod metadata and compatibility state.
    /// </summary>
    public partial class ModInfo : ObservableObject
    {
        public string Id { get; set; } = string.Empty;
        public string Name { get; set; } = string.Empty;
        public string Version { get; set; } = string.Empty;
        public string MinecraftVersion { get; set; } = "1.21+";
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

        [ObservableProperty]
        private ModCompatibilityState _compatibility = ModCompatibilityState.Compatible;

        [ObservableProperty]
        private string _compatibilityReason = string.Empty;

        public string StatusText => IsEnabled ? "ENABLED" : "DISABLED";
        public string StatusColor => IsEnabled ? "#10B981" : "#64748B";

        public string CompatibilityText => Compatibility switch
        {
            ModCompatibilityState.Compatible => "COMPATIBLE",
            ModCompatibilityState.UpdateAvailable => "UPDATE AVAILABLE",
            ModCompatibilityState.UnsupportedLoader => "UNSUPPORTED LOADER",
            ModCompatibilityState.UnsupportedMinecraftVersion => "UNSUPPORTED MC VERSION",
            ModCompatibilityState.Incompatible => "INCOMPATIBLE",
            _ => "COMPATIBLE"
        };

        public string CompatibilityColor => Compatibility switch
        {
            ModCompatibilityState.Compatible => "#10B981",
            ModCompatibilityState.UpdateAvailable => "#38BDF8",
            ModCompatibilityState.UnsupportedLoader => "#F59E0B",
            ModCompatibilityState.UnsupportedMinecraftVersion => "#EF4444",
            ModCompatibilityState.Incompatible => "#EF4444",
            _ => "#10B981"
        };

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
