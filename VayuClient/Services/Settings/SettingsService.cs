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
            _settings = SafeJsonStorage.LoadSafe<LauncherSettings>(_settingsFilePath, () => new LauncherSettings()) ?? new LauncherSettings();
            CrashLogger.LogMessage($"[SettingsService]: Loaded user settings from {_settingsFilePath}");
        }

        public async Task SaveSettingsAsync(LauncherSettings settings)
        {
            try
            {
                _settings = settings;
                await SafeJsonStorage.SaveAtomicAsync(_settingsFilePath, settings);
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
                SafeJsonStorage.SaveAtomic(_settingsFilePath, settings);
                SettingsChanged?.Invoke(_settings);
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("SettingsService.SaveSettingsSync", ex);
            }
        }
    }
}
