using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Server
{
    public interface IServerService
    {
        IReadOnlyList<ServerInfo> GetServers();
        ServerInfo? GetServer(string id);
        Task AddServerAsync(ServerInfo server);
        Task UpdateServerAsync(ServerInfo server);
        Task DeleteServerAsync(string id);
        Task<bool> PingServerAsync(ServerInfo server, CancellationToken ct = default);
        Task PingAllServersAsync(IEnumerable<ServerInfo> servers, CancellationToken ct = default);
    }
}
