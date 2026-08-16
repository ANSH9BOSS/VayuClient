using System.Collections.Generic;

namespace VayuClient.Models
{
    public class ModLoaderInstallResult
    {
        public bool Success { get; set; } = true;
        public string? ErrorMessage { get; set; }
        public string? CustomMainClass { get; set; }
        public List<string> AdditionalLibraries { get; set; } = new();
        public List<string> AdditionalJvmArgs { get; set; } = new();
        public List<string> AdditionalGameArgs { get; set; } = new();
    }
}
