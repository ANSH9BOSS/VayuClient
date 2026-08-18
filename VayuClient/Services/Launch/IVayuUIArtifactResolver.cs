using System.Threading.Tasks;
using VayuClient.Models;
using VayuClient.Services.Java;

namespace VayuClient.Services.Launch
{
    public interface IVayuUIArtifactResolver
    {
        Task<string?> ResolveAndDeployAsync(MinecraftInstance instance, JavaRuntimeInfo javaRuntime);
    }
}
