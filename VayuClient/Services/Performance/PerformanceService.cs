using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using Newtonsoft.Json;
using VayuClient.Core;
using VayuClient.Models;
using VayuClient.Services.Hardware;

namespace VayuClient.Services.Performance
{
    public class PerformanceService : IPerformanceService
    {
        private readonly IHardwareInfoService _hardwareService;
        private PerformanceSettings _settings = new();
        private readonly string _settingsFilePath;

        public PerformanceSettings CurrentSettings => _settings;

        public PerformanceService(IHardwareInfoService hardwareService)
        {
            _hardwareService = hardwareService;

            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var vayuDir = Path.Combine(appData, "VayuClient");
            Directory.CreateDirectory(vayuDir);
            _settingsFilePath = Path.Combine(vayuDir, "performance_settings.json");

            LoadSettings();
        }

        public void UpdateSettings(PerformanceSettings settings)
        {
            _settings = settings;
            SaveSettings();
        }

        private void LoadSettings()
        {
            _settings = Core.SafeJsonStorage.LoadSafe<PerformanceSettings>(_settingsFilePath, () => new PerformanceSettings()) ?? new PerformanceSettings();
        }

        private void SaveSettings()
        {
            try
            {
                Core.SafeJsonStorage.SaveAtomic(_settingsFilePath, _settings);
            }
            catch { }
        }

        public string GenerateOptimizedJvmFlags(MinecraftInstance instance, int ramMB)
        {
            var profile = _hardwareService.GetHardwareProfile();
            int gcThreads = Math.Max(2, profile.PhysicalCores);

            var sb = new StringBuilder();
            sb.Append($"-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=20 ");
            sb.Append($"-XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ");
            sb.Append($"-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1ReservePercent=20 ");
            sb.Append($"-XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 ");
            sb.Append($"-XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 ");
            sb.Append($"-XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 ");
            sb.Append($"-XX:ParallelGCThreads={gcThreads} -XX:ConcGCThreads={Math.Max(1, gcThreads / 2)}");

            return sb.ToString().Trim();
        }

        public async Task ApplyInstanceOptionsAsync(MinecraftInstance instance, PerformanceSettings settings)
        {
            try
            {
                var gameDir = instance.GameDirectory;
                Directory.CreateDirectory(gameDir);

                var optionsPath = Path.Combine(gameDir, "options.txt");
                var optionsMap = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);

                if (File.Exists(optionsPath))
                {
                    var lines = await File.ReadAllLinesAsync(optionsPath);
                    foreach (var line in lines)
                    {
                        var idx = line.IndexOf(':');
                        if (idx > 0)
                        {
                            var key = line[..idx].Trim();
                            var val = line[(idx + 1)..].Trim();
                            optionsMap[key] = val;
                        }
                    }
                }

                // Apply real configuration values and uncap FPS limits
                optionsMap["entityDistanceScaling"] = settings.MobRenderDistanceScale.ToString("0.0#", CultureInfo.InvariantCulture);
                optionsMap["particles"] = settings.ParticleQuality.ToString();
                optionsMap["renderDistance"] = settings.RenderDistanceChunks.ToString();
                optionsMap["simulationDistance"] = settings.SimulationDistanceChunks.ToString();
                optionsMap["enableVsync"] = settings.EnableVsync ? "true" : "false";

                // Uncap framerate to Unlimited (260 in MC 1.20+) and disable background/AFK throttles
                optionsMap["maxFps"] = "260";
                optionsMap["inactivityFpsLimit"] = "\"none\"";
                optionsMap["chunkSectionFadeInTime"] = "0.0";
                optionsMap["renderClouds"] = "\"false\"";
                optionsMap["entityShadows"] = "false";
                optionsMap["biomeBlendRadius"] = "0";

                var outLines = new List<string>();
                foreach (var kvp in optionsMap)
                {
                    outLines.Add($"{kvp.Key}:{kvp.Value}");
                }

                await File.WriteAllLinesAsync(optionsPath, outLines);

                var configDir = Path.Combine(gameDir, "config");
                Directory.CreateDirectory(configDir);

                // Write Entity Culling mod configuration
                var entityCullingConfig = Path.Combine(configDir, "entityculling.json");
                var ecJson = JsonConvert.SerializeObject(new
                {
                    enabled = settings.EnableEntityCulling,
                    skipMarkerArmorStands = true,
                    cullTiles = true
                }, Formatting.Indented);
                await File.WriteAllTextAsync(entityCullingConfig, ecJson);

                // Write Sodium Max-Performance Configuration (Uncaps rendering pipeline)
                var sodiumConfig = Path.Combine(configDir, "sodium-options.json");
                var sodiumJson = JsonConvert.SerializeObject(new
                {
                    quality = new
                    {
                        hidden_fluid_culling = true,
                        improved_fluid_shaping = false,
                        use_closest_point_entity_sort = false,
                        pixel_filtering_mode = "NEAREST"
                    },
                    performance = new
                    {
                        chunk_builder_threads = 0,
                        chunk_build_defer_mode = "ALWAYS",
                        animate_only_visible_textures = true,
                        use_entity_culling = true,
                        use_fog_occlusion = true,
                        use_block_face_culling = true,
                        use_no_error_g_l_context = true,
                        quad_splitting_mode = "SAFE",
                        cpu_render_ahead_limit = 0
                    },
                    advanced = new
                    {
                        enable_memory_tracing = false,
                        allow_direct_memory_access = true,
                        use_advanced_staging_buffers = true
                    },
                    debug = new
                    {
                        terrain_sorting_enabled = false
                    },
                    notifications = new
                    {
                        has_cleared_donation_button = true,
                        has_seen_donation_prompt = true,
                        has_edited_fullscreen_option = true
                    }
                }, Formatting.Indented);
                await File.WriteAllTextAsync(sodiumConfig, sodiumJson);

                CrashLogger.LogMessage($"[PerformanceService]: Applied real performance settings to '{instance.Name}' (Options: RenderDistance={settings.RenderDistanceChunks}, EntityScale={settings.MobRenderDistanceScale}, Particles={settings.ParticleQuality}, MaxFPS=Unlimited)");
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("ApplyInstanceOptions", ex);
            }
        }

        public OptimizationRecommendation GetAiOptimizationRecommendation(MinecraftInstance instance)
        {
            var profile = _hardwareService.GetHardwareProfile();
            var rec = new OptimizationRecommendation();

            double ramGb = profile.TotalRamGB;
            rec.RecommendedRamMB = profile.RecommendedRamMB;

            bool isDiscreteGpu = profile.GpuName.Contains("NVIDIA", StringComparison.OrdinalIgnoreCase) ||
                                 profile.GpuName.Contains("Radeon", StringComparison.OrdinalIgnoreCase) ||
                                 profile.GpuName.Contains("RTX", StringComparison.OrdinalIgnoreCase) ||
                                 profile.GpuName.Contains("Arc", StringComparison.OrdinalIgnoreCase);

            if (isDiscreteGpu)
            {
                rec.RecommendedRenderDistance = ramGb >= 16 ? 16 : 12;
                rec.RecommendedSimulationDistance = 10;
                rec.Title = $"High-Performance Profile ({profile.GpuName})";
                rec.Description = $"Detected dedicated GPU '{profile.GpuName}' with {profile.DedicatedVramGB:0.#} GB VRAM and {ramGb:0.#} GB System RAM. Full hardware shaders and 144+ FPS headroom unlocked.";
            }
            else
            {
                rec.RecommendedRenderDistance = 10;
                rec.RecommendedSimulationDistance = 8;
                rec.Title = $"Balanced Integrated Profile ({profile.CpuName})";
                rec.Description = $"Optimized for balanced thermals and stable 60+ FPS on {profile.CpuName}.";
            }

            rec.AppliedOptimizations.Add($"RAM Allocated: {rec.RecommendedRamMB} MB (Preserves {ramGb - (rec.RecommendedRamMB / 1024.0):0.#} GB for OS & Apps)");
            rec.AppliedOptimizations.Add($"Target Render Distance: {rec.RecommendedRenderDistance} Chunks");
            rec.AppliedOptimizations.Add($"Entity Culling: Enabled (Saves up to 35% render overhead)");
            rec.AppliedOptimizations.Add($"G1GC Parallel GC Threads: {Math.Max(2, profile.PhysicalCores)}");

            return rec;
        }

        public ResourceMetricsSnapshot GetCurrentResourceMetrics()
        {
            var profile = _hardwareService.GetHardwareProfile();
            var snapshot = new ResourceMetricsSnapshot
            {
                TotalRamGB = profile.TotalRamGB,
                AvailableRamGB = profile.AvailableRamGB,
                RamUsagePercent = profile.TotalRamGB > 0
                    ? Math.Clamp((1.0 - (profile.AvailableRamGB / profile.TotalRamGB)) * 100.0, 0, 100)
                    : 0
            };

            try
            {
                using var proc = Process.GetCurrentProcess();
                snapshot.WorkingSetMB = proc.WorkingSet64 / (1024 * 1024);
            }
            catch { }

            return snapshot;
        }
    }
}
