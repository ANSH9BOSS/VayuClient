using System;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
using System.Linq;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using VayuClient.Core;

namespace VayuClient.Services.Discord
{
    public class DiscordRpcService : IDiscordRpcService
    {
        // Registered Discord Application Client ID for VayuClient
        private const string DefaultClientId = "1338504622652661830";
        private const int HandshakeOpcode = 0;
        private const int FrameOpcode = 1;
        private const int CloseOpcode = 2;

        private NamedPipeClientStream? _pipe;
        private readonly object _lock = new();
        private CancellationTokenSource? _cts;
        private bool _isInitialized;
        private bool _isEnabled = true;
        private bool _lastReportedConnectedState;
        private DateTime _sessionStartTime = DateTime.UtcNow;
        private DateTime _lastPushTime = DateTime.MinValue;

        private string _clientId = DefaultClientId;
        private string _currentDetails = "In Launcher";
        private string _currentState = "Ready to Play";
        private string? _currentLargeKey = "vayu_logo";
        private string? _currentLargeText = $"VayuClient v{AppInfo.VersionString}";
        private string? _currentSmallKey = "vayu_logo";
        private string? _currentSmallText = "Developer: ANSH9BOSS";
        private DateTime? _currentStartTime;

        public bool IsConnected
        {
            get
            {
                lock (_lock)
                {
                    return _pipe != null && _pipe.IsConnected;
                }
            }
        }

        public string ClientId
        {
            get => _clientId;
            set
            {
                if (_clientId != value && !string.IsNullOrWhiteSpace(value))
                {
                    _clientId = value.Trim();
                    if (_isInitialized)
                    {
                        Task.Run(async () =>
                        {
                            ClosePipe();
                            await Task.Delay(200);
                            TryConnect();
                        });
                    }
                }
            }
        }

        public bool IsEnabled
        {
            get => _isEnabled;
            set
            {
                if (_isEnabled != value)
                {
                    _isEnabled = value;
                    if (_isEnabled)
                    {
                        if (!IsConnected)
                        {
                            Task.Run(TryConnect);
                        }
                        else
                        {
                            PushPresence();
                        }
                    }
                    else
                    {
                        ClearPresence();
                    }
                }
            }
        }

        public void Initialize()
        {
            lock (_lock)
            {
                if (_isInitialized) return;
                _isInitialized = true;
                _sessionStartTime = DateTime.UtcNow;
                _currentStartTime = _sessionStartTime;
                _cts = new CancellationTokenSource();

                // Set default In-Launcher presence immediately so it's ready upon pipe connect
                SetInLauncherPresence();

                Task.Run(() => BackgroundConnectionLoopAsync(_cts.Token));
            }
        }

        public void SetInLauncherPresence(string? instanceName = null, string? version = null, string? loader = null)
        {
            string details = "In Launcher";
            string state = "Ready to Play";

            if (!string.IsNullOrWhiteSpace(version))
            {
                string loaderStr = !string.IsNullOrWhiteSpace(loader) && !loader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase)
                    ? $" ({loader})"
                    : "";
                details = $"Minecraft {version}{loaderStr}";
                state = !string.IsNullOrWhiteSpace(instanceName) ? instanceName : "Ready to Play";
            }
            else if (!string.IsNullOrWhiteSpace(instanceName))
            {
                state = instanceName;
            }

            UpdatePresence(
                details: details,
                state: state,
                largeImageKey: "vayu_logo",
                largeImageText: $"VayuClient v{AppInfo.VersionString}",
                smallImageKey: "vayu_logo",
                smallImageText: "Developer: ANSH9BOSS",
                startTime: _sessionStartTime);
        }

        public void SetLaunchingPresence(string instanceName, string version, string loader)
        {
            string loaderInfo = !string.IsNullOrWhiteSpace(loader) && !loader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase)
                ? $" ({loader})"
                : "";

            UpdatePresence(
                details: "Launching Minecraft",
                state: $"Minecraft {version}{loaderInfo}",
                largeImageKey: "vayu_logo",
                largeImageText: $"VayuClient v{AppInfo.VersionString}",
                smallImageKey: "vayu_logo",
                smallImageText: "Developer: ANSH9BOSS",
                startTime: DateTime.UtcNow);
        }

        public void SetInGamePresence(string instanceName, string version, string loader)
        {
            string loaderInfo = !string.IsNullOrWhiteSpace(loader) && !loader.Equals("Vanilla", StringComparison.OrdinalIgnoreCase)
                ? $" ({loader})"
                : "";

            UpdatePresence(
                details: $"Playing Minecraft {version}{loaderInfo}",
                state: "High-FPS Session Active",
                largeImageKey: "vayu_logo",
                largeImageText: $"VayuClient v{AppInfo.VersionString}",
                smallImageKey: "vayu_logo",
                smallImageText: "Developer: ANSH9BOSS",
                startTime: _sessionStartTime);
        }

        public void UpdatePresence(
            string details,
            string state = "Owned & Developed by ANSH9BOSS",
            string? largeImageKey = "vayu_logo",
            string? largeImageText = "VayuClient — Modern Minecraft Launcher",
            string? smallImageKey = "vayu_logo",
            string? smallImageText = "Developer: ANSH9BOSS",
            DateTime? startTime = null)
        {
            _currentDetails = details;
            _currentState = state;
            _currentLargeKey = largeImageKey;
            _currentLargeText = largeImageText;
            _currentSmallKey = smallImageKey;
            _currentSmallText = smallImageText;
            _currentStartTime = startTime ?? _sessionStartTime;

            if (_isEnabled)
            {
                if (IsConnected)
                {
                    PushPresence();
                }
                else
                {
                    Task.Run(TryConnect);
                }
            }
        }

        public void ClearPresence()
        {
            if (!IsConnected) return;

            try
            {
                var payload = new JObject
                {
                    ["cmd"] = "SET_ACTIVITY",
                    ["args"] = new JObject
                    {
                        ["pid"] = Process.GetCurrentProcess().Id,
                        ["activity"] = null
                    },
                    ["nonce"] = Guid.NewGuid().ToString("N")
                };

                SendPacket(FrameOpcode, payload.ToString(Formatting.None));
            }
            catch { }
        }

        private void PushPresence()
        {
            if (!_isEnabled || !IsConnected) return;

            try
            {
                var activity = new JObject
                {
                    ["details"] = _currentDetails,
                    ["state"] = _currentState,
                    ["type"] = 0 // Playing
                };

                if (_currentStartTime.HasValue)
                {
                    long epoch = (long)(_currentStartTime.Value.ToUniversalTime() - new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc)).TotalSeconds;
                    activity["timestamps"] = new JObject
                    {
                        ["start"] = epoch
                    };
                }

                var assets = new JObject();
                if (!string.IsNullOrEmpty(_currentLargeKey))
                {
                    assets["large_image"] = _currentLargeKey;
                    if (!string.IsNullOrEmpty(_currentLargeText)) assets["large_text"] = _currentLargeText;
                }
                if (!string.IsNullOrEmpty(_currentSmallKey))
                {
                    assets["small_image"] = _currentSmallKey;
                    if (!string.IsNullOrEmpty(_currentSmallText)) assets["small_text"] = _currentSmallText;
                }
                if (assets.Count > 0)
                {
                    activity["assets"] = assets;
                }

                var root = new JObject
                {
                    ["cmd"] = "SET_ACTIVITY",
                    ["args"] = new JObject
                    {
                        ["pid"] = Process.GetCurrentProcess().Id,
                        ["activity"] = activity
                    },
                    ["nonce"] = Guid.NewGuid().ToString("N")
                };

                SendPacket(FrameOpcode, root.ToString(Formatting.None));
                _lastPushTime = DateTime.UtcNow;
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("DiscordPushPresence", ex);
                ClosePipe();
            }
        }

        private async Task BackgroundConnectionLoopAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                try
                {
                    if (_isEnabled)
                    {
                        if (!IsConnected)
                        {
                            TryConnect();
                            // If still not connected, wait 15 seconds before next retry to avoid CPU/IO overhead
                            await Task.Delay(TimeSpan.FromSeconds(15), ct);
                        }
                        else
                        {
                            // Keep-alive refresh every 30 seconds
                            if ((DateTime.UtcNow - _lastPushTime).TotalSeconds >= 30)
                            {
                                PushPresence();
                            }
                            await Task.Delay(TimeSpan.FromSeconds(10), ct);
                        }
                    }
                    else
                    {
                        await Task.Delay(TimeSpan.FromSeconds(10), ct);
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch
                {
                    await Task.Delay(TimeSpan.FromSeconds(15), ct);
                }
            }
        }

        private bool TryConnect()
        {
            lock (_lock)
            {
                if (IsConnected) return true;

                for (int i = 0; i < 10; i++)
                {
                    NamedPipeClientStream? pipe = null;
                    try
                    {
                        var pipeName = $"discord-ipc-{i}";
                        pipe = new NamedPipeClientStream(".", pipeName, PipeDirection.InOut, PipeOptions.Asynchronous);
                        pipe.Connect(150);

                        if (pipe.IsConnected)
                        {
                            _pipe = pipe;

                            // Send Handshake
                            var handshake = new JObject
                            {
                                ["v"] = 1,
                                ["client_id"] = _clientId
                            };

                            SendPacket(HandshakeOpcode, handshake.ToString(Formatting.None));

                            // Read Handshake response with a short timeout
                            var response = ReadPacketWithTimeout(TimeSpan.FromSeconds(2));
                            if (!string.IsNullOrEmpty(response))
                            {
                                if (!_lastReportedConnectedState)
                                {
                                    _lastReportedConnectedState = true;
                                    CrashLogger.LogMessage($"[Discord RPC] Connected to {pipeName}");
                                }
                                PushPresence();
                                return true;
                            }
                            else
                            {
                                ClosePipe();
                            }
                        }
                    }
                    catch
                    {
                        if (pipe != null)
                        {
                            try { pipe.Dispose(); } catch { }
                        }
                        ClosePipe();
                    }
                }

                if (_lastReportedConnectedState)
                {
                    _lastReportedConnectedState = false;
                    CrashLogger.LogMessage("[Discord RPC] Disconnected");
                }

                return false;
            }
        }

        private void SendPacket(int opcode, string json)
        {
            lock (_lock)
            {
                if (_pipe == null || !_pipe.IsConnected) return;

                var bytes = Encoding.UTF8.GetBytes(json);
                using var ms = new MemoryStream();
                using var writer = new BinaryWriter(ms);

                writer.Write(opcode);
                writer.Write(bytes.Length);
                writer.Write(bytes);

                var packet = ms.ToArray();
                _pipe.Write(packet, 0, packet.Length);
                _pipe.Flush();
            }
        }

        private string? ReadPacketWithTimeout(TimeSpan timeout)
        {
            lock (_lock)
            {
                if (_pipe == null || !_pipe.IsConnected) return null;

                try
                {
                    byte[] buffer = new byte[8192];
                    var asyncResult = _pipe.BeginRead(buffer, 0, buffer.Length, null, null);
                    if (asyncResult.AsyncWaitHandle.WaitOne(timeout))
                    {
                        int bytesRead = _pipe.EndRead(asyncResult);
                        if (bytesRead >= 8)
                        {
                            int opcode = BitConverter.ToInt32(buffer, 0);
                            int length = BitConverter.ToInt32(buffer, 4);
                            if (opcode == FrameOpcode && length > 0)
                            {
                                return Encoding.UTF8.GetString(buffer, 8, Math.Min(length, bytesRead - 8));
                            }
                        }
                    }
                }
                catch { }

                return null;
            }
        }

        private void ClosePipe()
        {
            try
            {
                if (_pipe != null)
                {
                    if (_pipe.IsConnected)
                    {
                        try { SendPacket(CloseOpcode, "{}"); } catch { }
                    }
                    _pipe.Dispose();
                    _pipe = null;
                }
            }
            catch { }
        }

        public void Shutdown()
        {
            lock (_lock)
            {
                _cts?.Cancel();
                ClearPresence();
                ClosePipe();
                _isInitialized = false;
            }
        }

        public void Dispose()
        {
            Shutdown();
            _cts?.Dispose();
        }
    }
}
