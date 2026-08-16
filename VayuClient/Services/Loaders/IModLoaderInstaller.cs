using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Loaders
{
    public interface IModLoaderInstaller
    {
        Task<ModLoaderInstallResult> InstallLoaderAsync(
            MinecraftInstance instance,
            MojangVersionPackage baseMcPkg,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default);
    }
}
