using System;
using System.IO;
using CommunityToolkit.Mvvm.ComponentModel;
using Newtonsoft.Json;

namespace VayuClient.Models
{
    /// <summary>
    /// Represents an isolated Minecraft instance configuration.
    /// Saved to %APPDATA%\VayuClient\Instances\<Name>\instance.json
    /// </summary>
    public partial class MinecraftInstance : ObservableObject
    {
        [JsonProperty("instanceId")]
        public string InstanceId { get; set; } = Guid.NewGuid().ToString("N");

        [ObservableProperty]
        [JsonProperty("name")]
        private string _name = "My Minecraft Instance";

        [ObservableProperty]
        [JsonProperty("icon")]
        private string _icon = "🎮";

        [ObservableProperty]
        [JsonProperty("minecraftVersion")]
        private string _minecraftVersion = "1.21.4";

        [ObservableProperty]
        [JsonProperty("loader")]
        private string _loader = "Vanilla"; // Vanilla, Fabric, Forge, NeoForge, Quilt

        [ObservableProperty]
        [JsonProperty("loaderVersion")]
        private string _loaderVersion = "None";

        [ObservableProperty]
        [JsonProperty("ramMB")]
        private int _ramMB = 4096;

        [ObservableProperty]
        [JsonProperty("jvmArguments")]
        private string _jvmArguments = string.Empty;

        [JsonProperty("modpackId")]
        public string? ModpackId { get; set; }

        [JsonProperty("modpackVersion")]
        public string? ModpackVersion { get; set; }

        private string _gameDirectory = string.Empty;

        [JsonProperty("gameDirectory")]
        public string GameDirectory
        {
            get
            {
                if (!string.IsNullOrEmpty(_gameDirectory)) return _gameDirectory;
                var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                return Path.Combine(appData, "VayuClient", "Instances", string.IsNullOrEmpty(Name) ? "Default" : Name, "game");
            }
            set => _gameDirectory = value;
        }

        [JsonProperty("createdAt")]
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

        [JsonProperty("lastPlayedAt")]
        public DateTime? LastPlayedAt { get; set; }

        [ObservableProperty]
        [JsonIgnore]
        private bool _isActive;

        [JsonIgnore]
        public string DisplaySubtitle
        {
            get
            {
                if (!string.IsNullOrEmpty(LoaderVersion) && LoaderVersion != "None")
                {
                    return LoaderVersion;
                }
                if (!string.IsNullOrEmpty(Loader) && Loader != "Vanilla")
                {
                    return $"{Loader.ToLower()}-{MinecraftVersion}";
                }
                return MinecraftVersion;
            }
        }

        [JsonIgnore]
        public string DisplayRam => $"{RamMB} MB ({(RamMB / 1024.0):F1} GB)";

        [JsonIgnore]
        public string DisplayLastPlayed => LastPlayedAt.HasValue
            ? LastPlayedAt.Value.ToString("g")
            : "Never played";
    }
}
