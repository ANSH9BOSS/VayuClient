using System;
using System.Diagnostics;
using System.IO;
using System.IO.Pipes;
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
        // Verified registered Discord Application Client ID for VayuClient (Owner: ANSH9BOSS)
        private const string DefaultClientId = "1538504622652661830";
        private const int HandshakeOpcode = 0;
        private const int FrameOpcode = 1;
        private const int CloseOpcode = 2;

        private NamedPipeClientStream? _pipe;
        private readonly object _lock = new();
        private CancellationTokenSource? _cts;
        private bool _isInitialized;
        private bool _isEnabled = true;
        private DateTime _sessionStartTime = DateTime.UtcNow;
        private DateTime _lastPushTime = DateTime.MinValue;

        private string _clientId = DefaultClientId;
        private string _currentDetails = "In Launcher • Managing Instances & Mods";
        private string _currentState = "Owned & Developed by ANSH9BOSS";
        private string? _currentLargeKey = "vayu_logo";
        private string? _currentLargeText = $"VayuClient v{AppInfo.VersionString}";
        private string? _currentSmallKey = "vayu_logo";
        private string? _currentSmallText = "Developer: ANSH9BOSS";
        private DateTime? _currentStartTime;

        public bool IsConnected => _pipe != null && _pipe.IsConnected;

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

        public void SetEnabled(bool enabled)
        {
            IsEnabled = enabled;
            if (!enabled)
            {
                ClearPresence();
            }
            else
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

        public void SetInLauncherPresence()
        {
            UpdatePresence(
                details: "In Launcher • Managing Instances & Mods",
                state: "Owned & Developed by ANSH9BOSS",
                largeImageKey: "vayu_logo",
                largeImageText: $"VayuClient v{AppInfo.VersionString}",
                smallImageKey: "vayu_logo",
                smallImageText: "Developer: ANSH9BOSS",
                startTime: _sessionStartTime);
        }

        public void SetInGamePresence(string instanceName, string version, string loader)
        {
            string loaderInfo = !string.IsNullOrEmpty(loader) ? $" ({loader})" : "";
            UpdatePresence(
                details: $"Playing {instanceName}",
                state: $"Minecraft {version}{loaderInfo} • Owned & Developed by ANSH9BOSS",
                largeImageKey: "vayu_logo",
                largeImageText: $"VayuClient v{AppInfo.VersionString}",
                smallImageKey: "vayu_logo",
                smallImageText: "Developer: ANSH9BOSS",
                startTime: DateTime.UtcNow);
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
            catch (Exception ex)
            {
                CrashLogger.LogException("DiscordClearPresence", ex);
            }
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
                CrashLogger.LogMessage($"[Discord RPC]: Presence updated -> {_currentDetails} | {_currentState}");
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
                        }
                        else if ((DateTime.UtcNow - _lastPushTime).TotalSeconds >= 20)
                        {
                            // Keep-alive heartbeat refresh so Discord presence stays active in background
                            PushPresence();
                        }
                    }

                    await Task.Delay(TimeSpan.FromSeconds(4), ct);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    CrashLogger.LogException("DiscordBackgroundLoop", ex);
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
                        pipe.Connect(300);

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

                            // Read Handshake response asynchronously with a timeout
                            var response = ReadPacketWithTimeout(TimeSpan.FromSeconds(2));
                            if (!string.IsNullOrEmpty(response))
                            {
                                CrashLogger.LogMessage($"[Discord RPC]: Connected to Discord on pipe {pipeName}");
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
