using VayuClient.Models;

namespace VayuClient.Services.Launch
{
    public interface ILaunchArgumentBuilder
    {
        LaunchArgumentsResult BuildArguments(LaunchParameters parameters);
    }
}
