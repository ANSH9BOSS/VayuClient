using System.Collections.Generic;
using VayuClient.Models;

namespace VayuClient.Services.Java
{
    public interface IJavaRuntimeService
    {
        List<JavaRuntimeInfo> DetectInstalledRuntimes();
        JavaRuntimeInfo? FindCompatibleRuntime(int requiredMajorVersion);
        int GetRequiredJavaVersion(string minecraftVersion, int manifestMajorVersion = 0);
    }
}
