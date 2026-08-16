using Newtonsoft.Json;

namespace VayuClient.Models
{
    /// <summary>
    /// Represents a Minecraft version entry.
    /// </summary>
    public class MinecraftVersion
    {
        [JsonProperty("id")]
        public string Id { get; set; } = string.Empty;

        [JsonProperty("type")]
        public string Type { get; set; } = "release"; // release, snapshot, old_beta, old_alpha

        [JsonProperty("url")]
        public string Url { get; set; } = string.Empty;

        [JsonProperty("releaseDate")]
        public DateTime ReleaseDate { get; set; }

        [JsonProperty("isInstalled")]
        public bool IsInstalled { get; set; }

        [JsonProperty("modLoader")]
        public string? ModLoader { get; set; } // Fabric, Forge, NeoForge, null

        public string DisplayType => Type switch
        {
            "release" => "Release",
            "snapshot" => "Snapshot",
            "old_beta" => "Old Beta",
            "old_alpha" => "Old Alpha",
            _ => Type
        };
    }
}
