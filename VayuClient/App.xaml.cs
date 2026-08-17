using System;
using System.Windows;
using VayuClient.Core;

namespace VayuClient
{
    public partial class App : Application
    {
        public App()
        {
            CrashLogger.Initialize();
        }

        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);
            CrashLogger.Initialize();
        }

        protected override void OnExit(ExitEventArgs e)
        {
            try
            {
                var discordRpc = ServiceLocator.Resolve<Services.Discord.IDiscordRpcService>();
                discordRpc?.Shutdown();
            }
            catch { }

            base.OnExit(e);
        }
    }
}
