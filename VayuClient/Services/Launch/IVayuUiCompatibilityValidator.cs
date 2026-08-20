using System;
using System.Collections.Generic;

namespace VayuClient.Services.Launch
{
    public class VayuUiArtifactInfo
    {
        public string FilePath { get; set; } = string.Empty;
        public int BytecodeMajor { get; set; }
        public int BytecodeMinor { get; set; }
        public int RequiredJavaMajor { get; set; }
        public string VayuUiVersion { get; set; } = "1.7.0";
        public string MinecraftCompatibility { get; set; } = string.Empty;
        public List<string> SupportedLoaders { get; set; } = new();
        public bool HasManifest { get; set; }
        public bool IsValid { get; set; }
        public string ErrorMessage { get; set; } = string.Empty;
    }

    public interface IVayuUiCompatibilityValidator
    {
        VayuUiArtifactInfo InspectArtifact(string jarPath);
        bool ValidateCompatibility(int jvmJavaMajor, string jarPath, string minecraftVersion, out string failureReason);
        bool ValidateCompatibility(int jvmJavaMajor, string jarPath, string minecraftVersion, string loader, out string failureReason);
        void PurgeIncompatibleUiMods(string modsDirectory, int jvmJavaMajor, string minecraftVersion, string? loader = null);
    }
}
