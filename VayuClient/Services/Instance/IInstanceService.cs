using System.Collections.Generic;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Instance
{
    public interface IInstanceService
    {
        IReadOnlyList<MinecraftInstance> GetAllInstances();
        MinecraftInstance? GetActiveInstance();
        void SetActiveInstance(string instanceId);
        Task<MinecraftInstance> CreateInstanceAsync(MinecraftInstance instance);
        Task SaveInstanceAsync(MinecraftInstance instance);
        void DeleteInstance(string instanceId);
        event Action? InstancesChanged;
    }
}
