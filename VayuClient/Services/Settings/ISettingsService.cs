using System;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Settings
{
    public interface ISettingsService
    {
        LauncherSettings Settings { get; }
        Task SaveSettingsAsync(LauncherSettings settings);
        void SaveSettingsSync(LauncherSettings settings);
        void LoadSettings();
        event Action<LauncherSettings>? SettingsChanged;
    }
}
