using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;
using Newtonsoft.Json;

namespace VayuClient.Models
{
    public class MojangVersionPackage
    {
        [JsonProperty("id")]
        public string Id { get; set; } = string.Empty;

        [JsonProperty("mainClass")]
        public string MainClass { get; set; } = "net.minecraft.client.main.Main";

        [JsonProperty("minecraftArguments")]
        public string? MinecraftArguments { get; set; }

        [JsonProperty("arguments")]
        public MojangArguments? Arguments { get; set; }

        [JsonProperty("assetIndex")]
        public MojangAssetIndexInfo? AssetIndex { get; set; }

        [JsonProperty("assets")]
        public string? Assets { get; set; }

        [JsonProperty("downloads")]
        public MojangDownloads? Downloads { get; set; }

        [JsonProperty("libraries")]
        public List<MojangLibrary> Libraries { get; set; } = new();

        [JsonProperty("javaVersion")]
        public MojangJavaVersionInfo? JavaVersion { get; set; }

        [JsonProperty("type")]
        public string Type { get; set; } = "release";

        [JsonProperty("releaseTime")]
        public DateTime ReleaseTime { get; set; }
    }

    public class MojangDownloads
    {
        [JsonProperty("client")]
        public MojangDownloadArtifact? Client { get; set; }

        [JsonProperty("server")]
        public MojangDownloadArtifact? Server { get; set; }
    }

    public class MojangDownloadArtifact
    {
        [JsonProperty("url")]
        public string Url { get; set; } = string.Empty;

        [JsonProperty("sha1")]
        public string Sha1 { get; set; } = string.Empty;

        [JsonProperty("size")]
        public long Size { get; set; }

        [JsonProperty("path")]
        public string? Path { get; set; }
    }

    public class MojangAssetIndexInfo
    {
        [JsonProperty("id")]
        public string Id { get; set; } = string.Empty;

        [JsonProperty("sha1")]
        public string Sha1 { get; set; } = string.Empty;

        [JsonProperty("size")]
        public long Size { get; set; }

        [JsonProperty("totalSize")]
        public long TotalSize { get; set; }

        [JsonProperty("url")]
        public string Url { get; set; } = string.Empty;
    }

    public class MojangLibrary
    {
        [JsonProperty("name")]
        public string Name { get; set; } = string.Empty;

        [JsonProperty("downloads")]
        public MojangLibraryDownloads? Downloads { get; set; }

        [JsonProperty("rules")]
        public List<MojangRule>? Rules { get; set; }

        [JsonProperty("natives")]
        public Dictionary<string, string>? Natives { get; set; }
    }

    public class MojangLibraryDownloads
    {
        [JsonProperty("artifact")]
        public MojangDownloadArtifact? Artifact { get; set; }

        [JsonProperty("classifiers")]
        public Dictionary<string, MojangDownloadArtifact>? Classifiers { get; set; }
    }

    public class MojangRule
    {
        [JsonProperty("action")]
        public string Action { get; set; } = "allow"; // allow / disallow

        [JsonProperty("os")]
        public MojangOsRule? Os { get; set; }

        [JsonProperty("features")]
        public Dictionary<string, bool>? Features { get; set; }
    }

    public class MojangOsRule
    {
        [JsonProperty("name")]
        public string? Name { get; set; } // windows, osx, linux

        [JsonProperty("version")]
        public string? Version { get; set; }

        [JsonProperty("arch")]
        public string? Arch { get; set; }
    }

    public class MojangArguments
    {
        [JsonProperty("game")]
        public List<object>? Game { get; set; }

        [JsonProperty("jvm")]
        public List<object>? Jvm { get; set; }
    }

    public class MojangJavaVersionInfo
    {
        [JsonProperty("component")]
        public string Component { get; set; } = "java-runtime-gamma";

        [JsonProperty("majorVersion")]
        public int MajorVersion { get; set; } = 21;
    }

    public class MojangAssetObject
    {
        [JsonProperty("hash")]
        public string Hash { get; set; } = string.Empty;

        [JsonProperty("size")]
        public long Size { get; set; }
    }

    public class MojangAssetIndexFile
    {
        [JsonProperty("objects")]
        public Dictionary<string, MojangAssetObject> Objects { get; set; } = new();
    }
}
