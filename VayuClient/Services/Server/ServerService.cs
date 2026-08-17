using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Sockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;

namespace VayuClient.Services.Server
{
    /// <summary>
    /// Minecraft Java Edition Server List Ping (SLP) implementation.
    /// Protocol: https://wiki.vg/Server_List_Ping
    /// Real TCP-based handshake + status request, NOT ICMP ping.
    /// Background-thread safe; never blocks the UI thread.
    /// </summary>
    public class ServerService : IServerService
    {
        private const int PingTimeoutMs = 5000;
        private const int MaxParallelPings = 4;

        private readonly string _filePath;
        private List<ServerInfo> _servers = new();
        private readonly object _lock = new();

        public ServerService()
        {
            var appData = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient");
            Directory.CreateDirectory(appData);
            _filePath = Path.Combine(appData, "servers.json");
            Load();
        }

        // ─── Persistence ──────────────────────────────────────────────────────

        private void Load()
        {
            lock (_lock)
            {
                try
                {
                    _servers = SafeJsonStorage.LoadSafe<List<ServerInfo>>(_filePath) ?? new();
                }
                catch
                {
                    _servers = new();
                }
            }
        }

        private async Task SaveAsync()
        {
            List<ServerInfo> snapshot;
            lock (_lock) { snapshot = _servers.ToList(); }
            await SafeJsonStorage.SaveAtomicAsync(_filePath, snapshot);
        }

        public IReadOnlyList<ServerInfo> GetServers()
        {
            lock (_lock) { return _servers.AsReadOnly(); }
        }

        public ServerInfo? GetServer(string id)
        {
            lock (_lock) { return _servers.FirstOrDefault(s => s.Id == id); }
        }

        public async Task AddServerAsync(ServerInfo server)
        {
            lock (_lock) { _servers.Add(server); }
            await SaveAsync();
        }

        public async Task UpdateServerAsync(ServerInfo server)
        {
            lock (_lock)
            {
                var idx = _servers.FindIndex(s => s.Id == server.Id);
                if (idx >= 0) _servers[idx] = server;
            }
            await SaveAsync();
        }

        public async Task DeleteServerAsync(string id)
        {
            lock (_lock) { _servers.RemoveAll(s => s.Id == id); }
            await SaveAsync();
        }

        // ─── Ping ─────────────────────────────────────────────────────────────

        public async Task<bool> PingServerAsync(ServerInfo server, CancellationToken ct = default)
        {
            server.UpdateRuntimeState(ServerPingStatus.Pinging, -1);

            try
            {
                using var linked = CancellationTokenSource.CreateLinkedTokenSource(ct);
                linked.CancelAfter(PingTimeoutMs);

                var sw = System.Diagnostics.Stopwatch.StartNew();
                var result = await DoSlpAsync(server.Address, server.Port, linked.Token);
                sw.Stop();

                if (result != null)
                {
                    server.UpdateRuntimeState(
                        ServerPingStatus.Online,
                        (int)sw.ElapsedMilliseconds,
                        result.Motd,
                        result.OnlinePlayers,
                        result.MaxPlayers,
                        result.Version,
                        result.FaviconBase64);
                    return true;
                }
            }
            catch { }

            server.UpdateRuntimeState(ServerPingStatus.Offline, -1);
            return false;
        }

        public async Task PingAllServersAsync(IEnumerable<ServerInfo> servers, CancellationToken ct = default)
        {
            var sem = new SemaphoreSlim(MaxParallelPings, MaxParallelPings);
            await Task.WhenAll(servers.Select(async sv =>
            {
                await sem.WaitAsync(ct);
                try { await PingServerAsync(sv, ct); }
                finally { sem.Release(); }
            }));
        }

        // ─── Minecraft SLP (Java Edition) ─────────────────────────────────────

        private static async Task<SlpResult?> DoSlpAsync(string host, int port, CancellationToken ct)
        {
            try
            {
                using var tcp = new TcpClient { NoDelay = true };
                var conn = tcp.ConnectAsync(host, port);
                if (await Task.WhenAny(conn, Task.Delay(PingTimeoutMs, ct)) != conn || !tcp.Connected)
                    return null;

                using var ns = tcp.GetStream();
                ns.ReadTimeout = PingTimeoutMs;
                ns.WriteTimeout = PingTimeoutMs;

                // 1. Handshake packet (ID 0x00)
                var hostBytes = Encoding.UTF8.GetBytes(host);
                using var hs = new MemoryStream();
                WriteVI(hs, 0x00);                           // Packet ID
                WriteVI(hs, 47);                             // Protocol version (any — server ignores in status mode)
                WriteVI(hs, hostBytes.Length);
                hs.Write(hostBytes, 0, hostBytes.Length);
                hs.WriteByte((byte)(port >> 8));
                hs.WriteByte((byte)(port & 0xFF));
                WriteVI(hs, 1);                              // Next state: Status
                await Send(ns, hs.ToArray(), ct);

                // 2. Status Request (ID 0x00, empty)
                await Send(ns, new byte[] { 0x00 }, ct);

                // 3. Read Status Response
                var pkt = await Recv(ns, ct);
                if (pkt == null || pkt.Length < 2) return null;
                int pos = 0;
                if (RVI(pkt, ref pos) != 0x00) return null;
                int jlen = RVI(pkt, ref pos);
                if (jlen <= 0 || pos + jlen > pkt.Length) return null;
                return ParseSlp(Encoding.UTF8.GetString(pkt, pos, jlen));
            }
            catch { return null; }
        }

        private static SlpResult? ParseSlp(string json)
        {
            try
            {
                var o = JObject.Parse(json);

                // MOTD: plain string or Chat object
                string motd;
                if (o["description"] is JObject desc)
                {
                    motd = desc["text"]?.Value<string>() ?? "";
                    var extras = desc["extra"] as JArray;
                    if (extras != null)
                        foreach (var e in extras) motd += e["text"]?.Value<string>() ?? "";
                }
                else
                {
                    motd = o["description"]?.Value<string>() ?? "";
                }
                // Strip Minecraft section-sign color codes
                motd = Regex.Replace(motd, "\u00A7.", "").Trim();

                int online = o["players"]?["online"]?.Value<int>() ?? 0;
                int max    = o["players"]?["max"]?.Value<int>() ?? 0;
                string ver = o["version"]?["name"]?.Value<string>() ?? "";
                string? fav = o["favicon"]?.Value<string>();
                if (fav != null && fav.Contains(","))
                    fav = fav[(fav.IndexOf(',') + 1)..];

                return new SlpResult(motd, online, max, ver, fav);
            }
            catch { return null; }
        }

        // ─── Low-level VarInt packet I/O ──────────────────────────────────────

        private static async Task Send(NetworkStream ns, byte[] payload, CancellationToken ct)
        {
            using var ms = new MemoryStream();
            WriteVI(ms, payload.Length);
            ms.Write(payload, 0, payload.Length);
            var buf = ms.ToArray();
            await ns.WriteAsync(buf, 0, buf.Length, ct);
        }

        private static async Task<byte[]?> Recv(NetworkStream ns, CancellationToken ct)
        {
            int len = 0, shift = 0;
            while (true)
            {
                var b = new byte[1];
                if (await ns.ReadAsync(b, 0, 1, ct) == 0) return null;
                len |= (b[0] & 0x7F) << shift;
                if ((b[0] & 0x80) == 0) break;
                shift += 7;
                if (shift >= 35) return null;
            }
            if (len <= 0 || len > 1_048_576) return null;
            var buf = new byte[len]; int tot = 0;
            while (tot < len)
            {
                int r = await ns.ReadAsync(buf, tot, len - tot, ct);
                if (r == 0) break;
                tot += r;
            }
            return tot == len ? buf : null;
        }

        private static void WriteVI(Stream s, int v)
        {
            uint u = (uint)v;
            while (true)
            {
                if ((u & ~0x7Fu) == 0) { s.WriteByte((byte)u); return; }
                s.WriteByte((byte)((u & 0x7F) | 0x80));
                u >>= 7;
            }
        }

        private static int RVI(byte[] d, ref int i)
        {
            int r = 0, sh = 0;
            while (i < d.Length)
            {
                byte b = d[i++];
                r |= (b & 0x7F) << sh;
                if ((b & 0x80) == 0) break;
                sh += 7;
            }
            return r;
        }

        private record SlpResult(string Motd, int OnlinePlayers, int MaxPlayers, string Version, string? FaviconBase64);
    }
}
