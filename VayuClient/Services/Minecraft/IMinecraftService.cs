namespace VayuClient.Services.Minecraft
{
    /// <summary>
    /// Minecraft service interface. Phase 2+: version manifest, downloading, installation.
    /// </summary>
    public interface IMinecraftService
    {
        Task<bool> IsMinecraftInstalledAsync(string version);
    }
}
