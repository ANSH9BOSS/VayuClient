using System.Collections.Generic;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Version
{
    public interface IVersionService
    {
        string LatestRelease { get; }
        string LatestSnapshot { get; }
        Task<List<MinecraftVersion>> GetManifestVersionsAsync(bool forceRefresh = false);
        Task<List<LoaderInfo>> GetCompatibleLoadersAsync(string mcVersion);
        Task<List<string>> GetLoaderVersionsAsync(string loaderName, string mcVersion);
        Task<List<ModpackInfo>> SearchModrinthModpacksAsync(string query, string mcVersion, string loader);
    }
}
