namespace VayuClient.Models
{
    public class LoaderInfo
    {
        public string Name { get; set; } = "Vanilla"; // Vanilla, Fabric, Forge, NeoForge, Quilt
        public string Description { get; set; } = "No mod loader";
        public bool IsCompatible { get; set; } = true;
        public string CompatibilityNote { get; set; } = "Compatible";
        public List<string> AvailableVersions { get; set; } = new();
    }
}
