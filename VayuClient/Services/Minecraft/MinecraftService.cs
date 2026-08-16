namespace VayuClient.Services.Minecraft
{
    public class MinecraftService : IMinecraftService
    {
        public Task<bool> IsMinecraftInstalledAsync(string version)
            => Task.FromResult(false);
    }
}
