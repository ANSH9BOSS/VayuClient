using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Java
{
    public interface IJavaRuntimeService
    {
        List<JavaRuntimeInfo> DetectInstalledRuntimes();
        JavaRuntimeInfo? FindCompatibleRuntime(int requiredMajorVersion);
        int GetRequiredJavaVersion(string minecraftVersion, int manifestMajorVersion = 0);
        Task<JavaRuntimeInfo?> EnsureJavaRuntimeAsync(int requiredMajorVersion, IProgress<DownloadProgressInfo>? progress = null, CancellationToken ct = default);
    }
}
