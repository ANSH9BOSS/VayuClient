using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Java;

namespace VayuClient.Services.Launch
{
    public class VayuUIArtifactResolver : IVayuUIArtifactResolver
    {
        private readonly IVayuUiCompatibilityValidator _validator;

        public VayuUIArtifactResolver(IVayuUiCompatibilityValidator? validator = null)
        {
            _validator = validator ?? new VayuUiCompatibilityValidator();
        }

        public async Task<string?> ResolveAndDeployAsync(MinecraftInstance instance, JavaRuntimeInfo javaRuntime)
        {
            if (instance == null) return null;

            string mcVersion = instance.MinecraftVersion ?? "1.21.4";
            string loader = instance.Loader?.ToString() ?? "Fabric";
            int jvmMajor = javaRuntime?.MajorVersion ?? 21;

            CrashLogger.LogMessage($"[VayuUI Resolver] Resolving UI artifact for Minecraft {mcVersion}, Loader {loader}, JVM Java {jvmMajor}...");

            // 1. Locate instance mods directory
            string instPath = instance.GameDirectory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Instances", instance.Name ?? "Default");
            string modsDir = Path.Combine(instPath, "mods");
            Directory.CreateDirectory(modsDir);

            // 2. Locate master source UI artifacts
            string appBase = AppDomain.CurrentDomain.BaseDirectory;
            string assetsModsDir = Path.Combine(appBase, "Assets", "Mods");

            // Look for candidate UI JAR
            string candidateJar = Path.Combine(assetsModsDir, "vayuclient-ui-1.6.0.jar");
            if (!File.Exists(candidateJar))
            {
                string fallback = Path.Combine(appBase, "..", "..", "..", "Assets", "Mods", "vayuclient-ui-1.6.0.jar");
                if (File.Exists(fallback)) candidateJar = Path.GetFullPath(fallback);
            }
            if (!File.Exists(candidateJar))
            {
                string distFallback = Path.Combine(appBase, "vayuclient-ui-1.6.0.jar");
                if (File.Exists(distFallback)) candidateJar = distFallback;
            }

            if (!File.Exists(candidateJar))
            {
                CrashLogger.LogMessage($"[VayuUI Resolver] Notice: No Vayu UI artifact found at {candidateJar}. Native fallback active.");
                return null;
            }

            // 3. Inspect Candidate Bytecode and Compatibility
            var info = _validator.InspectArtifact(candidateJar);
            if (!_validator.ValidateCompatibility(jvmMajor, candidateJar, mcVersion, out string failureReason))
            {
                CrashLogger.LogMessage($"[VayuUI Resolver] Incompatible artifact ({failureReason}). Safely falling back to native Minecraft screens.");
                PurgeStaleUiMods(modsDir);
                return null;
            }

            // 4. Clean Stale UI Mods & Deploy Matched Artifact
            PurgeStaleUiMods(modsDir);

            string targetJar = Path.Combine(modsDir, "vayuclient-ui-1.6.0.jar");
            try
            {
                await Task.Run(() => File.Copy(candidateJar, targetJar, true));
                CrashLogger.LogMessage($"[VayuUI Resolver] Successfully deployed compatible UI artifact (Bytecode {info.BytecodeMajor}) to {targetJar}");
                return targetJar;
            }
            catch (Exception ex)
            {
                CrashLogger.LogMessage($"[VayuUI Resolver] Warning: Could not deploy artifact: {ex.Message}. Falling back to native.");
                return null;
            }
        }

        private static void PurgeStaleUiMods(string modsDir)
        {
            if (!Directory.Exists(modsDir)) return;
            try
            {
                var stale = Directory.GetFiles(modsDir, "*vayuclient-ui*.jar", SearchOption.TopDirectoryOnly);
                foreach (var f in stale)
                {
                    try { File.Delete(f); } catch { }
                }
            }
            catch { }
        }
    }
}
