using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net.Http;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using System.Windows.Media.Media3D;
using VayuClient.Models;

namespace VayuClient.Controls
{
    public enum PlayerModelMode
    {
        FullBody,
        HeadOnly
    }

    public enum PlayerPose
    {
        Running,     // Sprint cycle with swinging limbs and stride bounce
        Idle,        // Heroic standing with gentle breathing and head sway
        Combat,      // PvP battle-ready guard with attack swing arc
        Waving,      // Friendly greeting wave with raised arm
        CrossArms,   // Confident folded-arms stance
        Celebration, // Victorious arms-up jump & cheer
        Love         // Loving cuddle holding mini player companion in lap
    }

    /// <summary>
    /// Renders a full 3D animated Minecraft player model textured with real player skin.
    /// Features:
    /// - Authentic official Minecraft Steve skin byte data with genuine pixel shading.
    /// - 7 Animated 3D Poses (Running, Idle, Combat, Waving, CrossArms, Celebration, Love/Lap Cradle).
    /// - Interactive 360° Mouse Orbit Dragging + Click-to-cycle pose switching.
    /// - Ultra-sharp isolated face extraction (32x Nearest-Neighbor replication, zero blur/bleed).
    /// - Full support for modern 64x64 dual-layer skins (Hat, Jacket, Sleeves, Pants 3D overlays).
    /// - Official Mojang textures.minecraft.net direct high-res downloader + multi-CDN fallback.
    /// </summary>
    public class PlayerBody3D : Viewport3D
    {
        public static readonly DependencyProperty ProfileProperty = DependencyProperty.Register(
            nameof(Profile), typeof(object), typeof(PlayerBody3D), new PropertyMetadata(null, OnProfileChanged));

        public static readonly DependencyProperty IsRunningProperty = DependencyProperty.Register(
            nameof(IsRunning), typeof(bool), typeof(PlayerBody3D), new PropertyMetadata(true, OnIsRunningChanged));

        public static readonly DependencyProperty DisplayModeProperty = DependencyProperty.Register(
            nameof(DisplayMode), typeof(PlayerModelMode), typeof(PlayerBody3D), new PropertyMetadata(PlayerModelMode.FullBody, OnDisplayModeChanged));

        public static readonly DependencyProperty PoseProperty = DependencyProperty.Register(
            nameof(Pose), typeof(PlayerPose), typeof(PlayerBody3D), new PropertyMetadata(PlayerPose.Running, OnPoseChanged));

        private static readonly HttpClient SkinClient = CreateSkinClient();
        private static readonly ConcurrentDictionary<string, Task<BitmapSource?>> SkinRequests = new(StringComparer.OrdinalIgnoreCase);
        private static readonly string SkinCacheDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Cache", "Skins");

        // Canonical Official 64x64 Minecraft Steve Skin (Byte-for-byte original Mojang texture with authentic pixel shading)
        private const string OfficialSteveSkinBase64 = "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAEwElEQVR4Xu1av2sUURgMqCABQQVBBK0SCdooMQQD5jSFkNgpKdIEwSZoZ6GYJohNUmlhqrSxsUlhYZM/If/TmdncrLOz3+7drcneXtyB4f36bvNm3vd2l32ZmOiD+3eudMHZqWtJyTrbn189LqVfb+xAwZ17N1LxqLsBH17MZniuDFDBXn5b6+TEkxjz640dXLSmP8r/wgDd+74VILJoC5wLA3TFNRsgHqRQv/mh7+v64vkwwO8BSoiE4KLSrzd2UNF+QwQpdnn6aobs9+s1DpraFDZ183Im5ZkFSjVBf6PX0WtEcSh9PrUjmiBIIVpX8V7yd5Foxvn10efzqR0qwifMvr03y91fn9a7v7+8S8qf79eSvkfT13OxINvs0zpNYJ/Pp3ZwQjpRXUkIhWAIp3gSY5Go6JqsM55tn0/t0ImxxCMM4iACQoGXT3ZS0agDaCMGffhNdC0aGhnUCAN8hShIyRV/+/RBQs0AJUX5yuv9QQ1B2+dTOzgRlFx5PsO/v15MX2jwaPu4NJdw6fZkJoaPPGaCXtMN8LbPp3bwrU3FuwkoIRomsKRoGqS/5fX0zfDurckM2e/zGTkWHu50lfPz8wlnZmYSenwOR0fp9lBj0Ycxv77TL5fDwUE34f5+Znt6WGX4hCicRnh8DsciVbyacNoGqNEeVhk+oSoZcNYGTB4epgY0MgPOdAscC9ct8M8Z8Gxurwvij6N8vvAjJftYkjp+cXc3Q0/PVHxv4kXxYKafQpW9uLS9tZWlxg4KChzUAB0HMdlL29sJU0E9oUxP74/iMyK9j/2RARsbJ6QBHB8UZQI9O3wchJALm5sJUdcURel9Hu8muFkZ8WJGMlZkADgoygQWGaB1iiH7CfL4UJi0c2mupHg1gRwULpDtqE/HSBeUWXEzQFc/MiC39/f/3uh4s9N6RI67zkK4Ab7CkSHa9pRODegJcQM83jMkFd9r66OON1Rtq+hKj8XIAN8GZeMURXoK66p6bBTvv3VhKj4ygKXrLASFFQnsZwAmqWKSDNAVZTb0BHl8aIDED2oAykoZ4OmtwpUeR1KQiqNwF8cYj8sYIOIR4yvrBqgRlQxYWVnpgn5zczLOqaIiM1Sgx6YGiGgVD6rgoizQLTC0AXi97XQ6GVGrq6s5oYjxWNR1i6hhmjk0gsI8RrePxiATIwM0K9wAlq6zEHzHpzgXSOFRHOpqAKnbiCVX1OP9XsMY9pcJ1/3vRrjOFi1atGjRosX4AR9Ai5h5fT44+ViajrVo0aJFixYtWtQN/5Q29OGqfELTDyEe1li4AUMfr4sBmZPlcYEbUCUD+JrLDBhrAypnwHFZ6airbujXX//I6WM6zpjcgaefEbDtbAoGEcls8DEwPOJWA9wEtpsCNwClG6Dj/QxID0vcCK83BS7QRfq49iUGlJ3xl5HQTOmZ498NnKdqoItnO+rTMdLP9/XG5wce2u/zGBmKBPtq67jGu1h99Hm7kY/FSGC/LaB0A4pEs5+lz2NkcIEoIwMic8DofM/Fq/DGZYCnv6+09kfbJNrnkXDv93mMDDxJpsAi+nE7WSY+Et44A3hcrqKG+f+CyAAV7Qaw7fMYGaL/G1CBFB7FoQ5BRfeByAiO+zyq4g8lK5z2I+oYkQAAAABJRU5ErkJggg==";

        private static readonly BitmapSource SteveSkinSource = LoadOfficialSteveSkin();

        // Transforms & Animation groups
        private readonly Model3DGroup _rootModelGroup = new();
        private readonly Model3DGroup _bodyGroup = new();
        private readonly Model3DGroup _headGroup = new();
        private readonly Model3DGroup _torsoGroup = new();
        private readonly Model3DGroup _rightArmGroup = new();
        private readonly Model3DGroup _leftArmGroup = new();
        private readonly Model3DGroup _rightLegGroup = new();
        private readonly Model3DGroup _leftLegGroup = new();
        private readonly Model3DGroup _miniPlayerGroup = new();

        // Limb Rotation Angles for Poses
        private readonly AxisAngleRotation3D _rightArmRotation = new(new Vector3D(1, 0, 0), 0);
        private readonly AxisAngleRotation3D _leftArmRotation = new(new Vector3D(1, 0, 0), 0);
        private readonly AxisAngleRotation3D _rightLegRotation = new(new Vector3D(1, 0, 0), 0);
        private readonly AxisAngleRotation3D _leftLegRotation = new(new Vector3D(1, 0, 0), 0);
        private readonly AxisAngleRotation3D _headNodRotation = new(new Vector3D(1, 0, 0), 3);
        private readonly TranslateTransform3D _bodyBounceTransform = new(0, 0, 0);
        private readonly ScaleTransform3D _miniPlayerScaleTransform = new(0, 0, 0);
        private readonly TranslateTransform3D _miniPlayerBounceTransform = new(0, 0, 0);

        // Overall Character Orientation (Heroic 3/4 Isometric Perspective + 360° Orbit)
        private readonly AxisAngleRotation3D _characterYawRotation = new(new Vector3D(0, 1, 0), -22);
        private readonly AxisAngleRotation3D _runningLeanRotation = new(new Vector3D(1, 0, 0), 8);

        // Mouse Drag Orbit state
        private bool _isMouseOrbitDragging;
        private Point _lastOrbitMousePos;
        private bool _hasDraggedSignificantly;

        private int _skinRequestId;
        private BitmapSource? _currentSkinSource;

        public PlayerBody3D()
        {
            ClipToBounds = false;
            BuildSceneHierarchy();
            ApplySkin(SteveSkinSource);

            Cursor = Cursors.Hand;
            ToolTip = "Drag left/right to rotate 360° • Click to cycle poses";

            IsVisibleChanged += (_, _) => UpdateAnimations();
            Loaded += (_, _) => UpdateAnimations();
            Unloaded += (_, _) => StopAnimations();
        }

        protected override void OnMouseLeftButtonDown(MouseButtonEventArgs e)
        {
            base.OnMouseLeftButtonDown(e);
            if (DisplayMode == PlayerModelMode.FullBody)
            {
                _isMouseOrbitDragging = true;
                _hasDraggedSignificantly = false;
                _lastOrbitMousePos = e.GetPosition(this);
                CaptureMouse();
                e.Handled = true;
            }
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            if (_isMouseOrbitDragging)
            {
                Point currentPos = e.GetPosition(this);
                double deltaX = currentPos.X - _lastOrbitMousePos.X;
                if (Math.Abs(deltaX) > 2.0 || _hasDraggedSignificantly)
                {
                    _hasDraggedSignificantly = true;
                    // Stop background yaw drift to give crisp user 360 orbit control
                    _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
                    _characterYawRotation.Angle += deltaX * 0.85;
                }
                _lastOrbitMousePos = currentPos;
                e.Handled = true;
            }
        }

        protected override void OnMouseLeftButtonUp(MouseButtonEventArgs e)
        {
            base.OnMouseLeftButtonUp(e);
            if (_isMouseOrbitDragging)
            {
                _isMouseOrbitDragging = false;
                ReleaseMouseCapture();

                if (!_hasDraggedSignificantly && DisplayMode == PlayerModelMode.FullBody)
                {
                    // Clean single click (not a drag) -> cycle to next pose!
                    var allPoses = (PlayerPose[])Enum.GetValues(typeof(PlayerPose));
                    int nextIndex = ((int)Pose + 1) % allPoses.Length;
                    Pose = allPoses[nextIndex];
                }
                e.Handled = true;
            }
        }

        public object? Profile
        {
            get => GetValue(ProfileProperty);
            set => SetValue(ProfileProperty, value);
        }

        public bool IsRunning
        {
            get => (bool)GetValue(IsRunningProperty);
            set => SetValue(IsRunningProperty, value);
        }

        public PlayerModelMode DisplayMode
        {
            get => (PlayerModelMode)GetValue(DisplayModeProperty);
            set => SetValue(DisplayModeProperty, value);
        }

        public PlayerPose Pose
        {
            get => (PlayerPose)GetValue(PoseProperty);
            set => SetValue(PoseProperty, value);
        }

        private static void OnProfileChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
            ((PlayerBody3D)d).LoadSkinForProfile(e.NewValue);

        private static void OnIsRunningChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
            ((PlayerBody3D)d).UpdateAnimations();

        private static void OnPoseChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
            ((PlayerBody3D)d).UpdateAnimations();

        private static void OnDisplayModeChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            var control = (PlayerBody3D)d;
            control.RebuildCameraAndMeshes();
        }

        private static BitmapSource LoadOfficialSteveSkin()
        {
            try
            {
                var bytes = Convert.FromBase64String(OfficialSteveSkinBase64);
                var img = new BitmapImage();
                img.BeginInit();
                img.CacheOption = BitmapCacheOption.OnLoad;
                img.StreamSource = new MemoryStream(bytes);
                img.EndInit();
                img.Freeze();
                return img;
            }
            catch
            {
                var fallback = BitmapSource.Create(64, 64, 96, 96, PixelFormats.Bgra32, null, new byte[64 * 64 * 4], 64 * 4);
                fallback.Freeze();
                return fallback;
            }
        }

        private void BuildSceneHierarchy()
        {
            UpdateCamera();

            var scene = new Model3DGroup();
            
            // Studio Lighting: Ambient fill + Crisp Directional Key Light + Purple Cyber Rim Light + Soft Under-Fill
            scene.Children.Add(new AmbientLight(Color.FromRgb(190, 185, 215)));
            scene.Children.Add(new DirectionalLight(Color.FromRgb(255, 255, 255), new Vector3D(-0.45, -0.75, -1.0)));
            scene.Children.Add(new DirectionalLight(Color.FromRgb(168, 85, 247), new Vector3D(0.8, 0.3, -0.6)));
            scene.Children.Add(new DirectionalLight(Color.FromRgb(60, 45, 90), new Vector3D(0.0, 1.0, -0.2)));

            // Attach limb transforms
            // Right arm pivot at shoulder: (-0.6, 0.95, 0)
            var rightArmTransform = new Transform3DGroup();
            rightArmTransform.Children.Add(new RotateTransform3D(_rightArmRotation, new Point3D(-0.6, 0.95, 0)));
            _rightArmGroup.Transform = rightArmTransform;

            // Left arm pivot at shoulder: (+0.6, 0.95, 0)
            var leftArmTransform = new Transform3DGroup();
            leftArmTransform.Children.Add(new RotateTransform3D(_leftArmRotation, new Point3D(0.6, 0.95, 0)));
            _leftArmGroup.Transform = leftArmTransform;

            // Right leg pivot at hip: (-0.2, 0.0, 0)
            var rightLegTransform = new Transform3DGroup();
            rightLegTransform.Children.Add(new RotateTransform3D(_rightLegRotation, new Point3D(-0.2, 0.0, 0)));
            _rightLegGroup.Transform = rightLegTransform;

            // Left leg pivot at hip: (+0.2, 0.0, 0)
            var leftLegTransform = new Transform3DGroup();
            leftLegTransform.Children.Add(new RotateTransform3D(_leftLegRotation, new Point3D(0.2, 0.0, 0)));
            _leftLegGroup.Transform = leftLegTransform;

            // Head pivot: (0, 1.2, 0)
            var headTransform = new Transform3DGroup();
            headTransform.Children.Add(new RotateTransform3D(_headNodRotation, new Point3D(0, 1.2, 0)));
            _headGroup.Transform = headTransform;

            // Assemble Body Group
            _bodyGroup.Children.Add(_headGroup);
            _bodyGroup.Children.Add(_torsoGroup);
            _bodyGroup.Children.Add(_rightArmGroup);
            _bodyGroup.Children.Add(_leftArmGroup);
            _bodyGroup.Children.Add(_rightLegGroup);
            _bodyGroup.Children.Add(_leftLegGroup);

            // Assemble Mini Companion Group (cradled in lap for Love pose)
            var miniTransform = new Transform3DGroup();
            miniTransform.Children.Add(_miniPlayerBounceTransform);
            miniTransform.Children.Add(_miniPlayerScaleTransform);
            _miniPlayerGroup.Transform = miniTransform;
            _bodyGroup.Children.Add(_miniPlayerGroup);

            // Root Transform Group (Running forward lean + 3/4 Isometric Yaw + Vertical Stride Bounce)
            var rootTransform = new Transform3DGroup();
            rootTransform.Children.Add(_bodyBounceTransform);
            rootTransform.Children.Add(new RotateTransform3D(_runningLeanRotation));
            rootTransform.Children.Add(new RotateTransform3D(_characterYawRotation));
            _rootModelGroup.Transform = rootTransform;

            _rootModelGroup.Children.Add(_bodyGroup);
            scene.Children.Add(_rootModelGroup);

            Children.Clear();
            Children.Add(new ModelVisual3D { Content = scene });
        }

        private void UpdateCamera()
        {
            if (DisplayMode == PlayerModelMode.HeadOnly)
            {
                // Eye-level tight framing directly on head center (Y = 1.6)
                Camera = new PerspectiveCamera(
                    new Point3D(0.35, 1.6, 2.5),
                    new Vector3D(-0.35, 0.0, -2.5),
                    new Vector3D(0, 1, 0),
                    28);
                
                _characterYawRotation.Angle = -14;
                _runningLeanRotation.Angle = 0;
            }
            else
            {
                // Full Body Heroic Camera (mathematical center Y = 0.42, full 3.25 unit height visible with no clipping)
                Camera = new PerspectiveCamera(
                    new Point3D(0.42, 0.42, 4.4),
                    new Vector3D(-0.42, 0.0, -4.4),
                    new Vector3D(0, 1, 0),
                    44);
                
                _characterYawRotation.Angle = -22;
                _runningLeanRotation.Angle = 8;
            }
        }

        private void RebuildCameraAndMeshes()
        {
            UpdateCamera();
            ApplySkin(_currentSkinSource ?? SteveSkinSource);
        }

        private async void LoadSkinForProfile(object? value)
        {
            string? username = null;
            string? uuid = null;
            bool isOffline = false;

            if (value is UserProfile profile)
            {
                username = profile.Username;
                uuid = profile.UUID;
                isOffline = profile.AccountType == AccountType.Offline;
            }
            else if (value is MinecraftInstance instance)
            {
                username = instance.Name;
            }
            else if (value is string text)
            {
                username = text;
            }

            if (string.IsNullOrWhiteSpace(username) || isOffline || 
                username.Equals("Offline", StringComparison.OrdinalIgnoreCase) || 
                username.Equals("Cracked", StringComparison.OrdinalIgnoreCase))
            {
                _currentSkinSource = SteveSkinSource;
                ApplySkin(SteveSkinSource);
                return;
            }

            // Immediately show Steve skin while loading so there is never an unrendered model
            ApplySkin(SteveSkinSource);
            var requestId = ++_skinRequestId;
            var cacheKey = $"{username.Trim()}_{(uuid ?? "")}".TrimEnd('_');
            var skin = await SkinRequests.GetOrAdd(cacheKey, _ => GetSkinAsync(username, uuid));
            
            if (requestId == _skinRequestId)
            {
                _currentSkinSource = skin ?? SteveSkinSource;
                ApplySkin(_currentSkinSource);
            }
        }

        private static async Task<BitmapSource?> GetSkinAsync(string username, string? uuid)
        {
            try
            {
                Directory.CreateDirectory(SkinCacheDirectory);
                var path = Path.Combine(SkinCacheDirectory, $"{username}.vayu-skin.png");
                
                byte[] bytes = Array.Empty<byte>();
                if (File.Exists(path))
                {
                    try
                    {
                        var cachedBytes = await File.ReadAllBytesAsync(path);
                        if (cachedBytes.Length > 200)
                        {
                            bytes = cachedBytes;
                        }
                    }
                    catch { }
                }

                if (bytes.Length == 0)
                {
                    bytes = await FetchSkinBytesAsync(username, uuid);
                    if (bytes.Length > 200)
                    {
                        try { await File.WriteAllBytesAsync(path, bytes); } catch { }
                    }
                }

                if (bytes.Length == 0)
                {
                    return SteveSkinSource;
                }

                var rawBitmap = new BitmapImage();
                rawBitmap.BeginInit();
                rawBitmap.CacheOption = BitmapCacheOption.OnLoad;
                rawBitmap.StreamSource = new MemoryStream(bytes);
                rawBitmap.EndInit();
                rawBitmap.Freeze();

                return rawBitmap;
            }
            catch
            {
                return SteveSkinSource;
            }
        }

        private static async Task<byte[]> FetchSkinBytesAsync(string username, string? uuid)
        {
            // 1. Try Official Mojang Session Server (textures.minecraft.net uncompressed source)
            try
            {
                string targetUuid = uuid ?? string.Empty;
                if (string.IsNullOrWhiteSpace(targetUuid))
                {
                    var userJson = await SkinClient.GetStringAsync($"https://api.mojang.com/users/profiles/minecraft/{Uri.EscapeDataString(username)}");
                    using var doc = JsonDocument.Parse(userJson);
                    if (doc.RootElement.TryGetProperty("id", out var idProp))
                    {
                        targetUuid = idProp.GetString() ?? string.Empty;
                    }
                }

                if (!string.IsNullOrWhiteSpace(targetUuid))
                {
                    var sessionJson = await SkinClient.GetStringAsync($"https://sessionserver.mojang.com/session/minecraft/profile/{targetUuid}");
                    using var sessionDoc = JsonDocument.Parse(sessionJson);
                    if (sessionDoc.RootElement.TryGetProperty("properties", out var props))
                    {
                        foreach (var prop in props.EnumerateArray())
                        {
                            if (prop.TryGetProperty("name", out var nameProp) && nameProp.GetString() == "textures" &&
                                prop.TryGetProperty("value", out var valProp))
                            {
                                var decodedJson = Encoding.UTF8.GetString(Convert.FromBase64String(valProp.GetString() ?? ""));
                                using var texDoc = JsonDocument.Parse(decodedJson);
                                if (texDoc.RootElement.TryGetProperty("textures", out var tex) &&
                                    tex.TryGetProperty("SKIN", out var skinProp) &&
                                    skinProp.TryGetProperty("url", out var urlProp))
                                {
                                    var skinUrl = urlProp.GetString();
                                    if (!string.IsNullOrEmpty(skinUrl))
                                    {
                                        var skinBytes = await SkinClient.GetByteArrayAsync(skinUrl);
                                        if (skinBytes != null && skinBytes.Length > 200)
                                            return skinBytes;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch { }

            // 2. Try Crafatar Skin CDN
            try
            {
                var bytes = await SkinClient.GetByteArrayAsync($"https://crafatar.com/skins/{Uri.EscapeDataString(uuid ?? username)}");
                if (bytes != null && bytes.Length > 200) return bytes;
            }
            catch { }

            // 3. Try Minotar Skin CDN
            try
            {
                var bytes = await SkinClient.GetByteArrayAsync($"https://minotar.net/skin/{Uri.EscapeDataString(username)}");
                if (bytes != null && bytes.Length > 200) return bytes;
            }
            catch { }

            // 4. Try NameMC scraping
            try
            {
                var profileHtml = await SkinClient.GetStringAsync($"https://namemc.com/profile/{Uri.EscapeDataString(username)}");
                var match = Regex.Match(profileHtml, @"(?:s(?:\\)?\.namemc(?:\\)?\.com(?:\\)?/i|/skin)(?:\\)?/([a-f0-9]{16})", RegexOptions.IgnoreCase);
                if (match.Success)
                {
                    var bytes = await SkinClient.GetByteArrayAsync($"https://s.namemc.com/i/{match.Groups[1].Value}.png");
                    if (bytes != null && bytes.Length > 200) return bytes;
                }
            }
            catch { }

            return Array.Empty<byte>();
        }

        private static HttpClient CreateSkinClient()
        {
            var client = new HttpClient { Timeout = TimeSpan.FromSeconds(8) };
            client.DefaultRequestHeaders.UserAgent.ParseAdd("VayuClient/3.0 (player 3D model renderer)");
            return client;
        }

        /// <summary>
        /// Extracts an isolated sub-rectangle of the skin and upscales it with 32x Nearest-Neighbor replication.
        /// Because the texture is standalone, DirectX clamping prevents ANY texture bleeding from adjacent faces.
        /// </summary>
        private static DiffuseMaterial? CreateFaceMaterial(uint[] skin, int skinW, int skinH,
            int tx, int ty, int tw, int th, int scale = 32, bool discardIfTransparent = false)
        {
            try
            {
                if (tx + tw > skinW || ty + th > skinH || tw <= 0 || th <= 0)
                    return null;

                bool hasAnyVisiblePixel = false;
                int dstW = tw * scale;
                int dstH = th * scale;
                var dstPixels = new uint[dstW * dstH];

                for (int y = 0; y < th; y++)
                {
                    for (int x = 0; x < tw; x++)
                    {
                        uint pixel = skin[(ty + y) * skinW + (tx + x)];
                        byte a = (byte)((pixel >> 24) & 0xFF);
                        if (a > 10) hasAnyVisiblePixel = true;

                        // Fill scale x scale block with identical pixel value
                        for (int dy = 0; dy < scale; dy++)
                        {
                            int row = (y * scale + dy) * dstW;
                            for (int dx = 0; dx < scale; dx++)
                            {
                                dstPixels[row + (x * scale + dx)] = pixel;
                            }
                        }
                    }
                }

                if (discardIfTransparent && !hasAnyVisiblePixel)
                    return null;

                var bitmap = BitmapSource.Create(dstW, dstH, 96, 96, PixelFormats.Bgra32, null, dstPixels, dstW * 4);
                bitmap.Freeze();

                var brush = new ImageBrush(bitmap)
                {
                    Stretch = Stretch.Fill,
                    TileMode = TileMode.None
                };
                RenderOptions.SetBitmapScalingMode(brush, BitmapScalingMode.NearestNeighbor);

                return new DiffuseMaterial(brush);
            }
            catch
            {
                return null;
            }
        }

        /// <summary>
        /// Programmatically creates the official 64x64 Minecraft Steve skin.
        /// </summary>
        private static BitmapSource GenerateSteveSkin()
        {
            int w = 64;
            int h = 64;
            var pixels = new uint[w * h];

            uint hairDark = 0xFF2B170B;
            uint hairMid = 0xFF452817;
            uint skinBase = 0xFFBC8B72;
            uint skinShade = 0xFFA5745D;
            uint eyeWhite = 0xFFFFFFFF;
            uint eyeBlue = 0xFF2B3B9B;
            uint beard = 0xFF593623;
            uint shirtTeal = 0xFF00A3A3;
            uint shirtShade = 0xFF008282;
            uint pantsBlue = 0xFF29337A;
            uint pantsShade = 0xFF1F265D;
            uint shoeGray = 0xFF464646;

            void FillRect(int rx, int ry, int rw, int rh, uint color)
            {
                for (int y = ry; y < ry + rh && y < h; y++)
                    for (int x = rx; x < rx + rw && x < w; x++)
                        pixels[y * w + x] = color;
            }

            // Head
            FillRect(8, 0, 8, 8, hairMid);
            FillRect(16, 0, 8, 8, skinShade);
            FillRect(0, 8, 8, 8, skinBase);
            FillRect(0, 8, 8, 3, hairMid);
            FillRect(8, 8, 8, 8, skinBase);
            FillRect(8, 8, 8, 3, hairMid);
            FillRect(8, 10, 1, 1, hairDark);
            FillRect(15, 10, 1, 1, hairDark);
            pixels[12 * w + 9] = eyeWhite;
            pixels[12 * w + 10] = eyeBlue;
            pixels[12 * w + 13] = eyeBlue;
            pixels[12 * w + 14] = eyeWhite;
            FillRect(11, 13, 2, 1, skinShade);
            FillRect(10, 14, 4, 1, beard);
            FillRect(16, 8, 8, 8, skinBase);
            FillRect(16, 8, 8, 3, hairMid);
            FillRect(24, 8, 8, 8, hairMid);

            // Torso
            FillRect(20, 16, 8, 4, shirtShade);
            FillRect(28, 16, 8, 4, shirtShade);
            FillRect(16, 20, 24, 12, shirtTeal);
            FillRect(22, 20, 4, 2, skinBase);

            // Right Arm
            FillRect(44, 16, 8, 4, shirtShade);
            FillRect(40, 20, 16, 4, shirtTeal);
            FillRect(40, 24, 16, 8, skinBase);

            // Left Arm
            FillRect(36, 48, 8, 4, shirtShade);
            FillRect(32, 52, 16, 4, shirtTeal);
            FillRect(32, 56, 16, 8, skinBase);

            // Right Leg
            FillRect(4, 16, 8, 4, pantsShade);
            FillRect(0, 20, 16, 8, pantsBlue);
            FillRect(0, 28, 16, 4, shoeGray);

            // Left Leg
            FillRect(20, 48, 8, 4, pantsShade);
            FillRect(16, 52, 16, 8, pantsBlue);
            FillRect(16, 60, 16, 4, shoeGray);

            var rawSteve = BitmapSource.Create(w, h, 96, 96, PixelFormats.Bgra32, null, pixels, w * 4);
            rawSteve.Freeze();
            return rawSteve;
        }

        private void ClearAllMeshes()
        {
            _headGroup.Children.Clear();
            _torsoGroup.Children.Clear();
            _rightArmGroup.Children.Clear();
            _leftArmGroup.Children.Clear();
            _rightLegGroup.Children.Clear();
            _leftLegGroup.Children.Clear();
            _miniPlayerGroup.Children.Clear();
        }

        private void ApplySkin(BitmapSource skinBitmap)
        {
            ClearAllMeshes();

            int skinW = skinBitmap.PixelWidth;
            int skinH = skinBitmap.PixelHeight;
            var format = PixelFormats.Bgra32;
            var converted = new FormatConvertedBitmap(skinBitmap, format, null, 0);
            var rawPixels = new uint[skinW * skinH];
            converted.CopyPixels(rawPixels, skinW * 4, 0);

            bool is64x64 = skinH >= 64;

            // ═══════════════════════════════════════════════════════════════
            // 1. HEAD (Inner 8x8x8 + Outer Hat 8.5x8.5x8.5)
            // ═══════════════════════════════════════════════════════════════
            // Inner Head: X [-0.4, 0.4], Y [1.2, 2.0], Z [-0.4, 0.4]
            AddCuboid(_headGroup, rawPixels, skinW, skinH,
                -0.4, 1.2, -0.4, 0.4, 2.0, 0.4,
                fX: 8, fY: 8, fW: 8, fH: 8,     // Front
                bkX: 24, bkY: 8, bkW: 8, bkH: 8, // Back
                rX: 0, rY: 8, rW: 8, rH: 8,      // Right
                lX: 16, lY: 8, lW: 8, lH: 8,     // Left
                tX: 8, tY: 0, tW: 8, tH: 8,      // Top
                bmX: 16, bmY: 0, bmW: 8, bmH: 8, // Bottom
                isOuterLayer: false);

            // Outer Head (Hat / Helmet): slightly expanded by 0.035
            AddCuboid(_headGroup, rawPixels, skinW, skinH,
                -0.435, 1.165, -0.435, 0.435, 2.035, 0.435,
                fX: 40, fY: 8, fW: 8, fH: 8,
                bkX: 56, bkY: 8, bkW: 8, bkH: 8,
                rX: 32, rY: 8, rW: 8, rH: 8,
                lX: 48, lY: 8, lW: 8, lH: 8,
                tX: 40, tY: 0, tW: 8, tH: 8,
                bmX: 48, bmY: 0, bmW: 8, bmH: 8,
                isOuterLayer: true);

            if (DisplayMode == PlayerModelMode.HeadOnly)
                return;

            // ═══════════════════════════════════════════════════════════════
            // 2. TORSO (Inner 8x12x4 + Outer Jacket)
            // ═══════════════════════════════════════════════════════════════
            // Inner Torso: X [-0.4, 0.4], Y [0.0, 1.2], Z [-0.2, 0.2]
            AddCuboid(_torsoGroup, rawPixels, skinW, skinH,
                -0.4, 0.0, -0.2, 0.4, 1.2, 0.2,
                fX: 20, fY: 20, fW: 8, fH: 12,
                bkX: 32, bkY: 20, bkW: 8, bkH: 12,
                rX: 16, rY: 20, rW: 4, rH: 12,
                lX: 28, lY: 20, lW: 4, lH: 12,
                tX: 20, tY: 16, tW: 8, tH: 4,
                bmX: 28, bmY: 16, bmW: 8, bmH: 4,
                isOuterLayer: false);

            if (is64x64)
            {
                // Outer Torso (Jacket): expanded by 0.025
                AddCuboid(_torsoGroup, rawPixels, skinW, skinH,
                    -0.425, -0.025, -0.225, 0.425, 1.225, 0.225,
                    fX: 20, fY: 36, fW: 8, fH: 12,
                    bkX: 32, bkY: 36, bkW: 8, bkH: 12,
                    rX: 16, rY: 36, rW: 4, rH: 12,
                    lX: 28, lY: 36, lW: 4, lH: 12,
                    tX: 20, tY: 32, tW: 8, tH: 4,
                    bmX: 28, bmY: 32, bmW: 8, bmH: 4,
                    isOuterLayer: true);
            }

            // ═══════════════════════════════════════════════════════════════
            // 3. RIGHT ARM (Inner 4x12x4 + Outer Sleeve)
            // ═══════════════════════════════════════════════════════════════
            // Inner Right Arm: X [-0.8, -0.4], Y [0.0, 1.2], Z [-0.2, 0.2]
            AddCuboid(_rightArmGroup, rawPixels, skinW, skinH,
                -0.8, 0.0, -0.2, -0.4, 1.2, 0.2,
                fX: 44, fY: 20, fW: 4, fH: 12,
                bkX: 52, bkY: 20, bkW: 4, bkH: 12,
                rX: 40, rY: 20, rW: 4, rH: 12,
                lX: 48, lY: 20, lW: 4, lH: 12,
                tX: 44, tY: 16, tW: 4, tH: 4,
                bmX: 48, bmY: 16, bmW: 4, bmH: 4,
                isOuterLayer: false);

            if (is64x64)
            {
                // Outer Right Arm (Sleeve): expanded by 0.025
                AddCuboid(_rightArmGroup, rawPixels, skinW, skinH,
                    -0.825, -0.025, -0.225, -0.375, 1.225, 0.225,
                    fX: 44, fY: 36, fW: 4, fH: 12,
                    bkX: 52, bkY: 36, bkW: 4, bkH: 12,
                    rX: 40, rY: 36, rW: 4, rH: 12,
                    lX: 48, lY: 36, lW: 4, lH: 12,
                    tX: 44, tY: 32, tW: 4, tH: 4,
                    bmX: 48, bmY: 32, bmW: 4, bmH: 4,
                    isOuterLayer: true);
            }

            // ═══════════════════════════════════════════════════════════════
            // 4. LEFT ARM (Inner 4x12x4 + Outer Sleeve)
            // ═══════════════════════════════════════════════════════════════
            if (is64x64)
            {
                // Inner Left Arm: X [0.4, 0.8], Y [0.0, 1.2], Z [-0.2, 0.2]
                AddCuboid(_leftArmGroup, rawPixels, skinW, skinH,
                    0.4, 0.0, -0.2, 0.8, 1.2, 0.2,
                    fX: 36, fY: 52, fW: 4, fH: 12,
                    bkX: 44, bkY: 52, bkW: 4, bkH: 12,
                    rX: 32, rY: 52, rW: 4, rH: 12,
                    lX: 40, lY: 52, lW: 4, lH: 12,
                    tX: 36, tY: 48, tW: 4, tH: 4,
                    bmX: 40, bmY: 48, bmW: 4, bmH: 4,
                    isOuterLayer: false);

                // Outer Left Arm (Sleeve): expanded by 0.025
                AddCuboid(_leftArmGroup, rawPixels, skinW, skinH,
                    0.375, -0.025, -0.225, 0.825, 1.225, 0.225,
                    fX: 52, fY: 52, fW: 4, fH: 12,
                    bkX: 60, bkY: 52, bkW: 4, bkH: 12,
                    rX: 48, rY: 52, rW: 4, rH: 12,
                    lX: 56, lY: 52, lW: 4, lH: 12,
                    tX: 52, tY: 48, tW: 4, tH: 4,
                    bmX: 56, bmY: 48, bmW: 4, bmH: 4,
                    isOuterLayer: true);
            }
            else
            {
                // Classic 64x32 Mirrored Arm
                AddCuboid(_leftArmGroup, rawPixels, skinW, skinH,
                    0.4, 0.0, -0.2, 0.8, 1.2, 0.2,
                    fX: 44, fY: 20, fW: 4, fH: 12,
                    bkX: 52, bkY: 20, bkW: 4, bkH: 12,
                    rX: 48, rY: 20, rW: 4, rH: 12,
                    lX: 40, lY: 20, lW: 4, lH: 12,
                    tX: 44, tY: 16, tW: 4, tH: 4,
                    bmX: 48, bmY: 16, bmW: 4, bmH: 4,
                    isOuterLayer: false);
            }

            // ═══════════════════════════════════════════════════════════════
            // 5. RIGHT LEG (Inner 4x12x4 + Outer Pants)
            // ═══════════════════════════════════════════════════════════════
            // Inner Right Leg: X [-0.4, 0.0], Y [-1.2, 0.0], Z [-0.2, 0.2]
            AddCuboid(_rightLegGroup, rawPixels, skinW, skinH,
                -0.4, -1.2, -0.2, 0.0, 0.0, 0.2,
                fX: 4, fY: 20, fW: 4, fH: 12,
                bkX: 12, bkY: 20, bkW: 4, bkH: 12,
                rX: 0, rY: 20, rW: 4, rH: 12,
                lX: 8, lY: 20, lW: 4, lH: 12,
                tX: 4, tY: 16, tW: 4, tH: 4,
                bmX: 8, bmY: 16, bmW: 4, bmH: 4,
                isOuterLayer: false);

            if (is64x64)
            {
                // Outer Right Leg (Pants): expanded by 0.025
                AddCuboid(_rightLegGroup, rawPixels, skinW, skinH,
                    -0.425, -1.225, -0.225, 0.025, 0.025, 0.225,
                    fX: 4, fY: 36, fW: 4, fH: 12,
                    bkX: 12, bkY: 36, bkW: 4, bkH: 12,
                    rX: 0, rY: 36, rW: 4, rH: 12,
                    lX: 8, lY: 36, lW: 4, lH: 12,
                    tX: 4, tY: 32, tW: 4, tH: 4,
                    bmX: 8, bmY: 32, bmW: 4, bmH: 4,
                    isOuterLayer: true);
            }

            // ═══════════════════════════════════════════════════════════════
            // 6. LEFT LEG (Inner 4x12x4 + Outer Pants)
            // ═══════════════════════════════════════════════════════════════
            if (is64x64)
            {
                // Inner Left Leg: X [0.0, 0.4], Y [-1.2, 0.0], Z [-0.2, 0.2]
                AddCuboid(_leftLegGroup, rawPixels, skinW, skinH,
                    0.0, -1.2, -0.2, 0.4, 0.0, 0.2,
                    fX: 20, fY: 52, fW: 4, fH: 12,
                    bkX: 28, bkY: 52, bkW: 4, bkH: 12,
                    rX: 16, rY: 52, rW: 4, rH: 12,
                    lX: 24, lY: 52, lW: 4, lH: 12,
                    tX: 20, tY: 48, tW: 4, tH: 4,
                    bmX: 24, bmY: 48, bmW: 4, bmH: 4,
                    isOuterLayer: false);

                // Outer Left Leg (Pants): expanded by 0.025
                AddCuboid(_leftLegGroup, rawPixels, skinW, skinH,
                    -0.025, -1.225, -0.225, 0.425, 0.025, 0.225,
                    fX: 4, fY: 52, fW: 4, fH: 12,
                    bkX: 12, bkY: 52, bkW: 4, bkH: 12,
                    rX: 0, rY: 52, rW: 4, rH: 12,
                    lX: 8, lY: 52, lW: 4, lH: 12,
                    tX: 4, tY: 48, tW: 4, tH: 4,
                    bmX: 8, bmY: 48, bmW: 4, bmH: 4,
                    isOuterLayer: true);
            }
            else
            {
                // Classic 64x32 Mirrored Leg
                AddCuboid(_leftLegGroup, rawPixels, skinW, skinH,
                    0.0, -1.2, -0.2, 0.4, 0.0, 0.2,
                    fX: 4, fY: 20, fW: 4, fH: 12,
                    bkX: 12, bkY: 20, bkW: 4, bkH: 12,
                    rX: 8, rY: 20, rW: 4, rH: 12,
                    lX: 0, lY: 20, lW: 4, lH: 12,
                    tX: 4, tY: 16, tW: 4, tH: 4,
                    bmX: 8, bmY: 16, bmW: 4, bmH: 4,
                    isOuterLayer: false);
            }

            // ═══════════════════════════════════════════════════════════════
            // 7. MINI PLAYER COMPANION (For Love / Lap Cradle Pose)
            // ═══════════════════════════════════════════════════════════════
            // Mini Head
            AddCuboid(_miniPlayerGroup, rawPixels, skinW, skinH,
                -0.11, 0.36, 0.26, 0.11, 0.58, 0.48,
                fX: 8, fY: 8, fW: 8, fH: 8,
                bkX: 24, bkY: 8, bkW: 8, bkH: 8,
                rX: 0, rY: 8, rW: 8, rH: 8,
                lX: 16, lY: 8, lW: 8, lH: 8,
                tX: 8, tY: 0, tW: 8, tH: 8,
                bmX: 16, bmY: 0, bmW: 8, bmH: 8,
                isOuterLayer: false);

            AddCuboid(_miniPlayerGroup, rawPixels, skinW, skinH,
                -0.12, 0.35, 0.25, 0.12, 0.59, 0.49,
                fX: 40, fY: 8, fW: 8, fH: 8,
                bkX: 56, bkY: 8, bkW: 8, bkH: 8,
                rX: 32, rY: 8, rW: 8, rH: 8,
                lX: 48, lY: 8, lW: 8, lH: 8,
                tX: 40, tY: 0, tW: 8, tH: 8,
                bmX: 48, bmY: 0, bmW: 8, bmH: 8,
                isOuterLayer: true);

            // Mini Torso
            AddCuboid(_miniPlayerGroup, rawPixels, skinW, skinH,
                -0.11, 0.08, 0.30, 0.11, 0.36, 0.44,
                fX: 20, fY: 20, fW: 8, fH: 12,
                bkX: 32, bkY: 20, bkW: 8, bkH: 12,
                rX: 16, rY: 20, rW: 4, rH: 12,
                lX: 28, lY: 20, lW: 4, lH: 12,
                tX: 20, tY: 16, tW: 8, tH: 4,
                bmX: 28, bmY: 16, bmW: 8, bmH: 4,
                isOuterLayer: false);

            // Mini Right Arm
            AddCuboid(_miniPlayerGroup, rawPixels, skinW, skinH,
                -0.18, 0.10, 0.30, -0.11, 0.36, 0.44,
                fX: 44, fY: 20, fW: 4, fH: 12,
                bkX: 52, bkY: 20, bkW: 4, bkH: 12,
                rX: 40, rY: 20, rW: 4, rH: 12,
                lX: 48, lY: 20, lW: 4, lH: 12,
                tX: 44, tY: 16, tW: 4, tH: 4,
                bmX: 48, bmY: 16, bmW: 4, bmH: 4,
                isOuterLayer: false);

            // Mini Left Arm
            AddCuboid(_miniPlayerGroup, rawPixels, skinW, skinH,
                0.11, 0.10, 0.30, 0.18, 0.36, 0.44,
                fX: 36, fY: 52, fW: 4, fH: 12,
                bkX: 44, bkY: 52, bkW: 4, bkH: 12,
                rX: 32, rY: 52, rW: 4, rH: 12,
                lX: 40, lY: 52, lW: 4, lH: 12,
                tX: 36, tY: 48, tW: 4, tH: 4,
                bmX: 40, bmY: 48, bmW: 4, bmH: 4,
                isOuterLayer: false);

            // Mini Right Leg (forward lap cradle)
            AddCuboid(_miniPlayerGroup, rawPixels, skinW, skinH,
                -0.11, 0.00, 0.30, -0.01, 0.10, 0.54,
                fX: 4, fY: 20, fW: 4, fH: 12,
                bkX: 12, bkY: 20, bkW: 4, bkH: 12,
                rX: 0, rY: 20, rW: 4, rH: 12,
                lX: 8, lY: 20, lW: 4, lH: 12,
                tX: 4, tY: 16, tW: 4, tH: 4,
                bmX: 8, bmY: 16, bmW: 4, bmH: 4,
                isOuterLayer: false);

            // Mini Left Leg (forward lap cradle)
            AddCuboid(_miniPlayerGroup, rawPixels, skinW, skinH,
                0.01, 0.00, 0.30, 0.11, 0.10, 0.54,
                fX: 20, fY: 52, fW: 4, fH: 12,
                bkX: 28, bkY: 52, bkW: 4, bkH: 12,
                rX: 16, rY: 52, rW: 4, rH: 12,
                lX: 24, lY: 52, lW: 4, lH: 12,
                tX: 20, tY: 48, tW: 4, tH: 4,
                bmX: 24, bmY: 48, bmW: 4, bmH: 4,
                isOuterLayer: false);
        }

        private void AddCuboid(Model3DGroup parentGroup, uint[] skin, int skinW, int skinH,
            double x1, double y1, double z1, double x2, double y2, double z2,
            int fX, int fY, int fW, int fH,
            int bkX, int bkY, int bkW, int bkH,
            int rX, int rY, int rW, int rH,
            int lX, int lY, int lW, int lH,
            int tX, int tY, int tW, int tH,
            int bmX, int bmY, int bmW, int bmH,
            bool isOuterLayer)
        {
            // Front (+Z)
            AddFace(parentGroup, skin, skinW, skinH, fX, fY, fW, fH, isOuterLayer,
                new Point3D(x1, y1, z2), new Point3D(x2, y1, z2),
                new Point3D(x2, y2, z2), new Point3D(x1, y2, z2));

            // Back (-Z)
            AddFace(parentGroup, skin, skinW, skinH, bkX, bkY, bkW, bkH, isOuterLayer,
                new Point3D(x2, y1, z1), new Point3D(x1, y1, z1),
                new Point3D(x1, y2, z1), new Point3D(x2, y2, z1));

            // Right (-X in 3D coordinate space)
            AddFace(parentGroup, skin, skinW, skinH, rX, rY, rW, rH, isOuterLayer,
                new Point3D(x1, y1, z1), new Point3D(x1, y1, z2),
                new Point3D(x1, y2, z2), new Point3D(x1, y2, z1));

            // Left (+X in 3D coordinate space)
            AddFace(parentGroup, skin, skinW, skinH, lX, lY, lW, lH, isOuterLayer,
                new Point3D(x2, y1, z2), new Point3D(x2, y1, z1),
                new Point3D(x2, y2, z1), new Point3D(x2, y2, z2));

            // Top (+Y)
            AddFace(parentGroup, skin, skinW, skinH, tX, tY, tW, tH, isOuterLayer,
                new Point3D(x1, y2, z2), new Point3D(x2, y2, z2),
                new Point3D(x2, y2, z1), new Point3D(x1, y2, z1));

            // Bottom (-Y)
            AddFace(parentGroup, skin, skinW, skinH, bmX, bmY, bmW, bmH, isOuterLayer,
                new Point3D(x1, y1, z1), new Point3D(x2, y1, z1),
                new Point3D(x2, y1, z2), new Point3D(x1, y1, z2));
        }

        private void AddFace(Model3DGroup group, uint[] skin, int skinW, int skinH,
            int tx, int ty, int tw, int th, bool isOuterLayer,
            Point3D a, Point3D b, Point3D c, Point3D d)
        {
            var mat = CreateFaceMaterial(skin, skinW, skinH, tx, ty, tw, th, 32, discardIfTransparent: isOuterLayer);
            if (mat == null) return;

            var mesh = new MeshGeometry3D();
            mesh.Positions.Add(a);
            mesh.Positions.Add(b);
            mesh.Positions.Add(c);
            mesh.Positions.Add(d);

            mesh.TextureCoordinates.Add(new Point(0, 1));
            mesh.TextureCoordinates.Add(new Point(1, 1));
            mesh.TextureCoordinates.Add(new Point(1, 0));
            mesh.TextureCoordinates.Add(new Point(0, 0));

            mesh.TriangleIndices.Add(0);
            mesh.TriangleIndices.Add(1);
            mesh.TriangleIndices.Add(2);
            mesh.TriangleIndices.Add(0);
            mesh.TriangleIndices.Add(2);
            mesh.TriangleIndices.Add(3);

            group.Children.Add(new GeometryModel3D(mesh, mat) { BackMaterial = mat });
        }

        private void UpdateAnimations()
        {
            StopAnimations();

            if (!IsLoaded || !IsVisible || !IsRunning || DisplayMode == PlayerModelMode.HeadOnly)
                return;

            var sineEase = new SineEase { EasingMode = EasingMode.EaseInOut };

            switch (Pose)
            {
                case PlayerPose.Love:
                {
                    // Sitting posture with mini player in lap
                    _runningLeanRotation.Angle = 6;
                    _rightLegRotation.Angle = 82;
                    _leftLegRotation.Angle = 82;

                    // Arms gently cradling the mini player
                    var armRightAnim = new DoubleAnimation(-64, -70, TimeSpan.FromSeconds(2.0))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _rightArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, armRightAnim);

                    var armLeftAnim = new DoubleAnimation(-64, -70, TimeSpan.FromSeconds(2.0))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _leftArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, armLeftAnim);

                    // Loving gaze looking down at lap
                    var headGaze = new DoubleAnimation(12, 16, TimeSpan.FromSeconds(2.0))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _headNodRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, headGaze);

                    // Gentle rocking lap bounce
                    var rockAnim = new DoubleAnimation(-0.16, -0.13, TimeSpan.FromSeconds(2.0))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _bodyBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, rockAnim);

                    // Make mini companion visible and animate breathing in lap
                    _miniPlayerScaleTransform.ScaleX = 1.0;
                    _miniPlayerScaleTransform.ScaleY = 1.0;
                    _miniPlayerScaleTransform.ScaleZ = 1.0;

                    var miniBob = new DoubleAnimation(0.0, 0.02, TimeSpan.FromSeconds(2.0))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _miniPlayerBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, miniBob);

                    var yawAnim = new DoubleAnimation(-24, -16, TimeSpan.FromSeconds(4.0))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, yawAnim);
                    break;
                }

                case PlayerPose.Idle:
                {
                    _runningLeanRotation.Angle = 0;
                    _rightLegRotation.Angle = 2;
                    _leftLegRotation.Angle = -2;

                    // Subtle breathing arm sway
                    var armAnim = new DoubleAnimation(-2, 4, TimeSpan.FromSeconds(2.5))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _rightArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, armAnim);

                    var leftArmAnim = new DoubleAnimation(4, -2, TimeSpan.FromSeconds(2.5))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _leftArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, leftArmAnim);

                    // Gentle breathing chest rise
                    var breathAnim = new DoubleAnimation(0.0, 0.015, TimeSpan.FromSeconds(1.8))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _bodyBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, breathAnim);

                    // Relaxed head sway
                    var headAnim = new DoubleAnimation(-2, 3, TimeSpan.FromSeconds(2.5))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _headNodRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, headAnim);

                    // Gentle panoramic yaw
                    var yawAnim = new DoubleAnimation(-24, -14, TimeSpan.FromSeconds(4.0))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, yawAnim);
                    break;
                }

                case PlayerPose.Combat:
                {
                    _runningLeanRotation.Angle = 10;
                    _rightLegRotation.Angle = 18;
                    _leftLegRotation.Angle = -15;
                    _headNodRotation.Angle = 5;

                    // Sword attack swing arc (-55° to -32°)
                    var attackAnim = new DoubleAnimation(-55, -32, TimeSpan.FromSeconds(0.75))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _rightArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, attackAnim);

                    // Shield guard raised (+22° to +28°)
                    var shieldAnim = new DoubleAnimation(22, 28, TimeSpan.FromSeconds(1.2))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _leftArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, shieldAnim);

                    // Battle bounce
                    var bounceAnim = new DoubleAnimation(0.0, 0.025, TimeSpan.FromSeconds(0.45))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _bodyBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, bounceAnim);

                    // Battle yaw angle
                    var yawAnim = new DoubleAnimation(-32, -22, TimeSpan.FromSeconds(2.8))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, yawAnim);
                    break;
                }

                case PlayerPose.Waving:
                {
                    _runningLeanRotation.Angle = 2;
                    _leftArmRotation.Angle = 2;
                    _rightLegRotation.Angle = 0;
                    _leftLegRotation.Angle = 0;

                    // Right arm raised high (-142°) and waving side-to-side (-156° to -128°)
                    var waveAnim = new DoubleAnimation(-156, -128, TimeSpan.FromSeconds(0.35))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _rightArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, waveAnim);

                    // Cheerful bob
                    var waveBounce = new DoubleAnimation(0.0, 0.02, TimeSpan.FromSeconds(0.35))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _bodyBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, waveBounce);

                    // Cheerful head tilt
                    var headTilt = new DoubleAnimation(-5, 5, TimeSpan.FromSeconds(0.7))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _headNodRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, headTilt);

                    var yawAnim = new DoubleAnimation(-20, -10, TimeSpan.FromSeconds(3.0))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, yawAnim);
                    break;
                }

                case PlayerPose.CrossArms:
                {
                    _runningLeanRotation.Angle = -2;
                    _rightArmRotation.Angle = -48;
                    _leftArmRotation.Angle = -48;
                    _rightLegRotation.Angle = 4;
                    _leftLegRotation.Angle = -4;

                    // Confident breathing rise
                    var breathAnim = new DoubleAnimation(0.0, 0.012, TimeSpan.FromSeconds(2.2))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _bodyBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, breathAnim);

                    // Calm head tilt
                    var headAnim = new DoubleAnimation(-2, 3, TimeSpan.FromSeconds(2.5))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _headNodRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, headAnim);

                    var yawAnim = new DoubleAnimation(-22, -14, TimeSpan.FromSeconds(3.5))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, yawAnim);
                    break;
                }

                case PlayerPose.Celebration:
                {
                    _runningLeanRotation.Angle = 4;

                    // Both arms raised high (-145° to -165°)
                    var armRightAnim = new DoubleAnimation(-145, -165, TimeSpan.FromSeconds(0.42))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _rightArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, armRightAnim);

                    var armLeftAnim = new DoubleAnimation(-145, -165, TimeSpan.FromSeconds(0.42))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _leftArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, armLeftAnim);

                    // Jumping leg stride
                    var legR = new DoubleAnimation(20, -15, TimeSpan.FromSeconds(0.42))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _rightLegRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, legR);

                    var legL = new DoubleAnimation(-20, 15, TimeSpan.FromSeconds(0.42))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _leftLegRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, legL);

                    // High victory jump bounce (Y: 0.0 to 0.10)
                    var jumpAnim = new DoubleAnimation(0.0, 0.10, TimeSpan.FromSeconds(0.42))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _bodyBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, jumpAnim);

                    // Joyful head tilt
                    var headJoy = new DoubleAnimation(-8, 2, TimeSpan.FromSeconds(0.84))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _headNodRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, headJoy);

                    var yawAnim = new DoubleAnimation(-26, -12, TimeSpan.FromSeconds(2.0))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, yawAnim);
                    break;
                }

                case PlayerPose.Running:
                default:
                {
                    _runningLeanRotation.Angle = 8;
                    _headNodRotation.Angle = 3;
                    var runningCycleDuration = TimeSpan.FromSeconds(0.68);

                    // 1. Right Arm Running Swing (-34° to +34°)
                    var rightArmAnim = new DoubleAnimation(-34, 34, runningCycleDuration)
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _rightArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, rightArmAnim);

                    // 2. Left Arm Running Swing (+34° to -34°)
                    var leftArmAnim = new DoubleAnimation(34, -34, runningCycleDuration)
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _leftArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, leftArmAnim);

                    // 3. Right Leg Running Swing (+32° to -32°)
                    var rightLegAnim = new DoubleAnimation(32, -32, runningCycleDuration)
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _rightLegRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, rightLegAnim);

                    // 4. Left Leg Running Swing (-32° to +32°)
                    var leftLegAnim = new DoubleAnimation(-32, 32, runningCycleDuration)
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _leftLegRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, leftLegAnim);

                    // 5. Vertical Running Stride Bobbing (Y: 0.0 to 0.06)
                    var bobbingAnim = new DoubleAnimation(0.0, 0.06, TimeSpan.FromSeconds(0.34))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _bodyBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, bobbingAnim);

                    // 6. Character Subtle Yaw Drift (-26° to -18°)
                    var yawAnim = new DoubleAnimation(-26, -18, TimeSpan.FromSeconds(3.5))
                    {
                        AutoReverse = true,
                        RepeatBehavior = RepeatBehavior.Forever,
                        EasingFunction = sineEase
                    };
                    _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, yawAnim);
                    break;
                }
            }
        }

        private void StopAnimations()
        {
            _rightArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _leftArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _rightLegRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _leftLegRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _headNodRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _bodyBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, null);
            _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _runningLeanRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);

            _miniPlayerScaleTransform.ScaleX = 0;
            _miniPlayerScaleTransform.ScaleY = 0;
            _miniPlayerScaleTransform.ScaleZ = 0;
            _miniPlayerBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, null);
        }
    }
}

