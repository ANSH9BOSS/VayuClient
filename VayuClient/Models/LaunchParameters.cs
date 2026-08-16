using System.Collections.Generic;

namespace VayuClient.Models
{
    public class LaunchParameters
    {
        public MinecraftInstance Instance { get; set; } = new();
        public UserProfile Profile { get; set; } = new();
        public MojangVersionPackage VersionPackage { get; set; } = new();
        public JavaRuntimeInfo JavaRuntime { get; set; } = new();
        public List<string> Classpath { get; set; } = new();
        public string InstanceNativesDir { get; set; } = string.Empty;
        public string SharedAssetsDir { get; set; } = string.Empty;
        public string? CustomMainClass { get; set; }
        public bool IsDemo { get; set; } = false;
        public int WindowWidth { get; set; } = 1280;
        public int WindowHeight { get; set; } = 720;
        public string? QuickPlayPath { get; set; }
        public string? QuickPlaySingleplayer { get; set; }
        public string? QuickPlayMultiplayer { get; set; }
        public string? QuickPlayRealms { get; set; }
        public List<string>? AdditionalJvmArgs { get; set; }
        public List<string>? AdditionalGameArgs { get; set; }
    }

    public class LaunchArgumentsResult
    {
        public string JavaExecutablePath { get; set; } = string.Empty;
        public List<string> JvmArguments { get; set; } = new();
        public string MainClass { get; set; } = string.Empty;
        public List<string> GameArguments { get; set; } = new();

        public string FullCommandLine => $"{QuoteIfSpaces(JavaExecutablePath)} {string.Join(" ", JvmArguments)} {MainClass} {string.Join(" ", GameArguments)}";

        public string SanitizedCommandLine
        {
            get
            {
                var copy = new List<string>(GameArguments);
                for (int i = 0; i < copy.Count; i++)
                {
                    if (copy[i] == "--accessToken" && i + 1 < copy.Count)
                    {
                        copy[i + 1] = "[PROTECTED_TOKEN]";
                    }
                }
                return $"{QuoteIfSpaces(JavaExecutablePath)} {string.Join(" ", JvmArguments)} {MainClass} {string.Join(" ", copy)}";
            }
        }

        private static string QuoteIfSpaces(string val)
        {
            return val.Contains(' ') ? $"\"{val}\"" : val;
        }
    }
}
