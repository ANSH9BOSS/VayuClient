using System;

namespace VayuClient.Services.Discord
{
    public interface IDiscordRpcService : IDisposable
    {
        bool IsConnected { get; }
        bool IsEnabled { get; set; }
        string ClientId { get; set; }

        void Initialize();
        void UpdatePresence(
            string details,
            string state = "Owned & Developed by ANSH9BOSS",
            string? largeImageKey = "vayu_logo",
            string? largeImageText = "VayuClient — Modern Minecraft Launcher",
            string? smallImageKey = "vayu_logo",
            string? smallImageText = "Developer: ANSH9BOSS",
            DateTime? startTime = null);
        void SetInLauncherPresence(string? instanceName = null, string? version = null, string? loader = null);
        void SetLaunchingPresence(string instanceName, string version, string loader);
        void SetInGamePresence(string instanceName, string version, string loader);
        void ClearPresence();
        void Shutdown();
    }
}
