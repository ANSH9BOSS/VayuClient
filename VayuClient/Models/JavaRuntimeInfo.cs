using System;

namespace VayuClient.Models
{
    public class JavaRuntimeInfo
    {
        public string Path { get; set; } = string.Empty;
        public string Version { get; set; } = string.Empty;
        public int MajorVersion { get; set; }
        public string Vendor { get; set; } = "Unknown";
        public bool Is64Bit { get; set; } = true;
        public string Architecture { get; set; } = "x64";

        public string DisplayName => $"{Vendor} Java {MajorVersion} ({Version}) [{Architecture}]";
    }
}
