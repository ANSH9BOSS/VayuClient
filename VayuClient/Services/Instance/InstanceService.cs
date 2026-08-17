using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;

namespace VayuClient.Services.Instance
{
    public class InstanceService : IInstanceService
    {
        private readonly string _instancesRootDir;
        private readonly List<MinecraftInstance> _instances = new();
        private MinecraftInstance? _activeInstance;

        public event Action? InstancesChanged;

        public InstanceService()
        {
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            _instancesRootDir = Path.Combine(appData, "VayuClient", "Instances");
            Directory.CreateDirectory(_instancesRootDir);
            LoadAllInstances();
        }

        public IReadOnlyList<MinecraftInstance> GetAllInstances() => _instances.AsReadOnly();

        public MinecraftInstance? GetActiveInstance() => _activeInstance;

        public void SetActiveInstance(string instanceId)
        {
            foreach (var inst in _instances)
            {
                inst.IsActive = (inst.InstanceId == instanceId);
            }
            _activeInstance = _instances.FirstOrDefault(i => i.IsActive);
            InstancesChanged?.Invoke();
        }

        public async Task<MinecraftInstance> CreateInstanceAsync(MinecraftInstance instance)
        {
            var safeName = string.Join("_", instance.Name.Split(Path.GetInvalidFileNameChars())).Trim();
            if (string.IsNullOrEmpty(safeName)) safeName = "Instance_" + Guid.NewGuid().ToString("N")[..6];

            var instanceDir = Path.Combine(_instancesRootDir, safeName);
            Directory.CreateDirectory(instanceDir);

            // Subfolders
            var gameDir = Path.Combine(instanceDir, "game");
            var modsDir = Path.Combine(instanceDir, "mods");
            var configDir = Path.Combine(instanceDir, "config");
            Directory.CreateDirectory(gameDir);
            Directory.CreateDirectory(modsDir);
            Directory.CreateDirectory(configDir);

            instance.GameDirectory = gameDir;
            instance.CreatedAt = DateTime.UtcNow;

            var jsonPath = Path.Combine(instanceDir, "instance.json");
            await SafeJsonStorage.SaveAtomicAsync(jsonPath, instance);

            // Add to in-memory list
            var existing = _instances.FirstOrDefault(i => i.InstanceId == instance.InstanceId);
            if (existing != null) _instances.Remove(existing);
            _instances.Add(instance);

            if (_instances.Count == 1 || instance.IsActive)
            {
                SetActiveInstance(instance.InstanceId);
            }
            else
            {
                InstancesChanged?.Invoke();
            }

            return instance;
        }

        public async Task SaveInstanceAsync(MinecraftInstance instance)
        {
            var instanceDir = Path.GetDirectoryName(instance.GameDirectory);
            if (string.IsNullOrEmpty(instanceDir) || !Directory.Exists(instanceDir))
            {
                var safeName = string.Join("_", instance.Name.Split(Path.GetInvalidFileNameChars())).Trim();
                instanceDir = Path.Combine(_instancesRootDir, safeName);
                Directory.CreateDirectory(instanceDir);
                instance.GameDirectory = Path.Combine(instanceDir, "game");
                Directory.CreateDirectory(instance.GameDirectory);
            }

            var jsonPath = Path.Combine(instanceDir, "instance.json");
            await SafeJsonStorage.SaveAtomicAsync(jsonPath, instance);

            var existingIndex = _instances.FindIndex(i => i.InstanceId == instance.InstanceId);
            if (existingIndex >= 0)
            {
                _instances[existingIndex] = instance;
            }
            else
            {
                _instances.Add(instance);
            }

            if (instance.IsActive || _activeInstance?.InstanceId == instance.InstanceId)
            {
                _activeInstance = instance;
            }

            InstancesChanged?.Invoke();
        }

        public void DeleteInstance(string instanceId)
        {
            var inst = _instances.FirstOrDefault(i => i.InstanceId == instanceId);
            if (inst == null) return;

            bool wasActive = inst.IsActive;
            _instances.Remove(inst);

            try
            {
                var instanceDir = Path.GetDirectoryName(inst.GameDirectory);
                if (!string.IsNullOrEmpty(instanceDir) && Directory.Exists(instanceDir))
                {
                    Directory.Delete(instanceDir, true);
                }
            }
            catch { }

            if (wasActive && _instances.Count > 0)
            {
                SetActiveInstance(_instances[0].InstanceId);
            }
            else
            {
                InstancesChanged?.Invoke();
            }
        }

        public void LoadAllInstances()
        {
            _instances.Clear();

            // Load only instances installed/created by the user in %APPDATA%\VayuClient\Instances
            if (Directory.Exists(_instancesRootDir))
            {
                foreach (var dir in Directory.GetDirectories(_instancesRootDir))
                {
                    var jsonPath = Path.Combine(dir, "instance.json");
                    if (File.Exists(jsonPath))
                    {
                        var inst = SafeJsonStorage.LoadSafe<MinecraftInstance>(jsonPath);
                        if (inst != null)
                        {
                            if (string.IsNullOrEmpty(inst.GameDirectory))
                            {
                                inst.GameDirectory = Path.Combine(dir, "game");
                            }
                            _instances.Add(inst);
                        }
                    }
                }
            }

            var active = _instances.FirstOrDefault(i => i.IsActive) ?? _instances.FirstOrDefault();
            if (active != null)
            {
                SetActiveInstance(active.InstanceId);
            }
            else
            {
                _activeInstance = null;
                InstancesChanged?.Invoke();
            }
        }
    }
}
