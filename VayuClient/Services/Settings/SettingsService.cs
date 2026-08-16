using System;
using System.IO;
using System.Threading.Tasks;
using Newtonsoft.Json;
using VayuClient.Core;
using VayuClient.Models;

namespace VayuClient.Services.Settings
{
    public class SettingsService : ISettingsService
    {
        private readonly string _settingsFilePath;
        private LauncherSettings _settings = new();

        public LauncherSettings Settings => _settings;
        public event Action<LauncherSettings>? SettingsChanged;

        public SettingsService()
        {
            var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            var vayuDir = Path.Combine(appData, "VayuClient");
            Directory.CreateDirectory(vayuDir);
            _settingsFilePath = Path.Combine(vayuDir, "Settings.json");

            LoadSettings();
        }

        public void LoadSettings()
        {
            try
            {
                if (File.Exists(_settingsFilePath))
                {
                    var json = File.ReadAllText(_settingsFilePath);
                    var loaded = JsonConvert.DeserializeObject<LauncherSettings>(json);
                    if (loaded != null)
                    {
                        _settings = loaded;
                        CrashLogger.LogMessage($"[SettingsService]: Loaded user settings from {_settingsFilePath}");
                        return;
                    }
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("SettingsService.LoadSettings", ex);
            }

            _settings = new LauncherSettings();
            SaveSettingsSync(_settings);
        }

        public async Task SaveSettingsAsync(LauncherSettings settings)
        {
            try
            {
                _settings = settings;
                var json = JsonConvert.SerializeObject(settings, Formatting.Indented);
                await File.WriteAllTextAsync(_settingsFilePath, json);
                CrashLogger.LogMessage($"[SettingsService]: Settings saved to {_settingsFilePath}");
                SettingsChanged?.Invoke(_settings);
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("SettingsService.SaveSettingsAsync", ex);
            }
        }

        public void SaveSettingsSync(LauncherSettings settings)
        {
            try
            {
                _settings = settings;
                var json = JsonConvert.SerializeObject(settings, Formatting.Indented);
                File.WriteAllText(_settingsFilePath, json);
                SettingsChanged?.Invoke(_settings);
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("SettingsService.SaveSettingsSync", ex);
            }
        }
    }
}
