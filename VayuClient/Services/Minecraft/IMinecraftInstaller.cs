using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Minecraft
{
    public interface IMinecraftInstaller
    {
        Task<MojangVersionPackage> GetVersionPackageAsync(string versionId, CancellationToken ct = default);
        Task<bool> InstallMinecraftAsync(string versionId, string instanceNativesDir, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default);
        List<string> ResolveClasspath(MojangVersionPackage pkg, string instanceNativesDir);
        bool IsLibraryAllowedOnWindows(MojangLibrary library);
    }
}
