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
    }
}
