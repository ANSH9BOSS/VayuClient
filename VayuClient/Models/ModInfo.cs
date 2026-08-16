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
        public string Author { get; set; } = string.Empty;
        public string Description { get; set; } = string.Empty;

        [ObservableProperty]
        private bool _isEnabled = true;

        [ObservableProperty]
        private string? _iconPath;

        public string ModLoader { get; set; } = "Fabric";
    }
}
