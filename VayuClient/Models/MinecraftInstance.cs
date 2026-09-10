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
        private string _minecraftVersion = "1.21.11";

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
        [JsonProperty("playtimeMinutes")]
        private int _playtimeMinutes = 0;

        [ObservableProperty]
        [JsonProperty("isFavorite")]
        private bool _isFavorite;

        [ObservableProperty]
        [JsonProperty("performanceProfile")]
        private string _performanceProfile = "High FPS";

        [ObservableProperty]
        [JsonProperty("artworkPath")]
        private string _artworkPath = string.Empty;

        [ObservableProperty]
        [JsonIgnore]
        private bool _isActive;

        [ObservableProperty]
        [JsonIgnore]
        private bool _isRunning;

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
        public string DisplayPlaytime
        {
            get
            {
                if (PlaytimeMinutes <= 0) return "0 hrs";
                if (PlaytimeMinutes < 60) return $"{PlaytimeMinutes} mins";
                double hours = PlaytimeMinutes / 60.0;
                return $"{hours:0.0} hrs";
            }
        }

        [JsonIgnore]
        public string DisplayLastPlayed => LastPlayedAt.HasValue
            ? LastPlayedAt.Value.ToString("MMM d, yyyy")
            : "Never played";

        [JsonIgnore]
        public string DisplayLastPlayedRelative
        {
            get
            {
                if (!LastPlayedAt.HasValue) return "Never played";
                var span = DateTime.UtcNow - LastPlayedAt.Value.ToUniversalTime();
                if (span.TotalMinutes < 5) return "Just now";
                if (span.TotalMinutes < 60) return $"{(int)span.TotalMinutes}m ago";
                if (span.TotalHours < 24) return $"{(int)span.TotalHours}h ago";
                if (span.TotalDays < 7) return $"{(int)span.TotalDays}d ago";
                return LastPlayedAt.Value.ToString("MMM d, yyyy");
            }
        }

        [JsonIgnore]
        public int ModCount
        {
            get
            {
                try
                {
                    var modsDir = Path.Combine(GameDirectory, "mods");
                    if (Directory.Exists(modsDir))
                    {
                        return Directory.GetFiles(modsDir, "*.jar").Length;
                    }
                }
                catch { }
                return 0;
            }
        }

        [JsonIgnore]
        public string DisplayModCount => ModCount == 1 ? "1 Mod" : $"{ModCount} Mods";

        [JsonIgnore]
        public string BannerArtwork
        {
            get
            {
                if (!string.IsNullOrEmpty(ArtworkPath))
                {
                    if (File.Exists(ArtworkPath) || ArtworkPath.StartsWith("/") || ArtworkPath.StartsWith("pack://"))
                        return ArtworkPath;
                }
                return GetThematicDefaultArtwork(Loader, ModpackId, Name);
            }
        }

        [JsonIgnore]
        public string LoaderAccentColor => (Loader ?? "").ToUpperInvariant() switch
        {
            "FABRIC" => "#00D2FF",
            "FORGE" => "#F59E0B",
            "NEOFORGE" => "#FB923C",
            "QUILT" => "#A855F7",
            _ => "#10B981" // Vanilla
        };

        [JsonIgnore]
        public string LoaderBadgeText => string.IsNullOrWhiteSpace(Loader) || Loader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase)
            ? "VANILLA"
            : Loader.ToUpperInvariant();

        public static string GetThematicDefaultArtwork(string loader, string? modpackId, string name)
        {
            var lower = (name + " " + (modpackId ?? "") + " " + loader).ToLowerInvariant();
            if (lower.Contains("pvp") || lower.Contains("bedwars") || lower.Contains("combat") || lower.Contains("duel"))
                return "/Assets/Images/bg_pvp_arena.jpg";
            if (lower.Contains("survival") || lower.Contains("hardcore") || lower.Contains("cave") || lower.Contains("lush"))
                return "/Assets/Images/bg_lush_caves.jpg";
            if (lower.Contains("fps") || lower.Contains("opti") || lower.Contains("sodium") || lower.Contains("speed"))
                return "/Assets/Images/bg_mountain_aurora.jpg";
            if (lower.Contains("mod") || lower.Contains("forge") || lower.Contains("tech") || lower.Contains("pack"))
                return "/Assets/Images/bg_cherry_grove.jpg";
            if (lower.Contains("ocean") || lower.Contains("water") || lower.Contains("depth"))
                return "/Assets/Images/bg_ocean_monument.jpg";
            if (lower.Contains("sky") || lower.Contains("island") || lower.Contains("fantasy"))
                return "/Assets/Images/bg_fantasy_islands.jpg";
            if (lower.Contains("nether") || lower.Contains("fire"))
                return "/Assets/Images/bg_cyber_nether.jpg";

            return "/Assets/Images/vayu_minecraft_hero.jpg";
        }
    }
}
