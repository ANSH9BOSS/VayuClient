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
        // Registered Discord Application Client ID for VayuClient
        private const string DefaultClientId = "1340275888998944800";
        private const int HandshakeOpcode = 0;
        private const int FrameOpcode = 1;
        private const int CloseOpcode = 2;

        private NamedPipeClientStream? _pipe;
        private readonly object _lock = new();
        private CancellationTokenSource? _cts;
        private bool _isInitialized;
        private bool _isEnabled = true;
        private DateTime _sessionStartTime = DateTime.UtcNow;

        private string _currentDetails = "In Main Menu";
        private string _currentState = "Owned & Developed by ANSH9BOSS";
        private string? _currentLargeKey = "vayu_logo";
        private string? _currentLargeText = $"VayuClient v{AppInfo.VersionString}";
        private string? _currentSmallKey = "steve";
        private string? _currentSmallText = "Developer: ANSH9BOSS";
        private DateTime? _currentStartTime;

        public bool IsConnected => _pipe != null && _pipe.IsConnected;

        public bool IsEnabled
        {
            get => _isEnabled;
            set
            {
                _isEnabled = value;
                if (!_isEnabled)
                {
                    ClearPresence();
                }
                else if (_isInitialized)
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

                Task.Run(() => BackgroundConnectionLoopAsync(_cts.Token));
            }
        }

        public void SetInLauncherPresence()
        {
            UpdatePresence(
                details: "Browsing Instances & Mods",
                state: "Owned & Developed by ANSH9BOSS",
                largeImageKey: "vayu_logo",
                largeImageText: $"VayuClient v{AppInfo.VersionString}",
                smallImageKey: "steve",
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
                smallImageKey: "steve",
                smallImageText: "Developer: ANSH9BOSS",
                startTime: DateTime.UtcNow);
        }

        public void UpdatePresence(
            string details,
            string state = "Owned & Developed by ANSH9BOSS",
            string? largeImageKey = "vayu_logo",
            string? largeImageText = "VayuClient — Modern Minecraft Launcher",
            string? smallImageKey = "steve",
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

            if (_isEnabled && IsConnected)
            {
                PushPresence();
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

                // Official VayuClient Buttons
                var buttons = new JArray
                {
                    new JObject
                    {
                        ["label"] = "Get VayuClient",
                        ["url"] = "https://github.com/ANSH9BOSS/VayuClient"
                    }
                };
                activity["buttons"] = buttons;

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
                CrashLogger.LogMessage($"[Discord RPC]: Presence updated -> {_currentDetails} | {_currentState}");
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("DiscordPushPresence", ex);
            }
        }

        private async Task BackgroundConnectionLoopAsync(CancellationToken ct)
        {
            while (!ct.IsCancellationRequested)
            {
                if (_isEnabled && !IsConnected)
                {
                    TryConnect();
                }

                try
                {
                    await Task.Delay(TimeSpan.FromSeconds(15), ct);
                }
                catch (OperationCanceledException)
                {
                    break;
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
                    try
                    {
                        var pipeName = $"discord-ipc-{i}";
                        var pipe = new NamedPipeClientStream(".", pipeName, PipeDirection.InOut, PipeOptions.Asynchronous);
                        pipe.Connect(250);

                        if (pipe.IsConnected)
                        {
                            _pipe = pipe;

                            // Send Handshake
                            var handshake = new JObject
                            {
                                ["v"] = 1,
                                ["client_id"] = DefaultClientId
                            };

                            SendPacket(HandshakeOpcode, handshake.ToString(Formatting.None));

                            // Read Handshake response
                            var response = ReadPacket();
                            CrashLogger.LogMessage($"[Discord RPC]: Connected to Discord on pipe {pipeName}");

                            // Push active presence
                            PushPresence();
                            return true;
                        }
                    }
                    catch
                    {
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

        private string? ReadPacket()
        {
            lock (_lock)
            {
                if (_pipe == null || !_pipe.IsConnected) return null;

                using var reader = new BinaryReader(_pipe, Encoding.UTF8, leaveOpen: true);
                int opcode = reader.ReadInt32();
                int length = reader.ReadInt32();
                if (length <= 0 || length > 1024 * 1024) return null;

                var bytes = reader.ReadBytes(length);
                return Encoding.UTF8.GetString(bytes);
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
