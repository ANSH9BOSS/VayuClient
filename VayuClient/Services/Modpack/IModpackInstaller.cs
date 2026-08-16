using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Modpack
{
    public interface IModpackInstaller
    {
        Task<bool> InstallModpackAsync(
            MinecraftInstance instance,
            string modpackId,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default);

        Task<bool> InstallLocalArchiveAsync(
            MinecraftInstance instance,
            string archivePath,
            IProgress<DownloadProgressInfo>? progress = null,
            CancellationToken ct = default);
    }
}
