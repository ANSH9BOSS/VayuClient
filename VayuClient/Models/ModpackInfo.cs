namespace VayuClient.Models
{
    public class ModpackInfo
    {
        public string Id { get; set; } = string.Empty;
        public string Title { get; set; } = string.Empty;
        public string Description { get; set; } = string.Empty;
        public string IconUrl { get; set; } = string.Empty;
        public string Author { get; set; } = string.Empty;
        public string LatestVersion { get; set; } = "1.0.0";
        public List<string> CompatibleVersions { get; set; } = new();
        public List<string> CompatibleLoaders { get; set; } = new();
        public bool IsCompatible { get; set; } = true;
        public string CompatibilityWarning { get; set; } = string.Empty;
        public string ProjectType { get; set; } = "modpack";
        public string DownloadsDisplay { get; set; } = "1.2M downloads";
        public bool HasIconUrl => !string.IsNullOrWhiteSpace(IconUrl);
    }
}
