using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net.Http;
using System.Text.RegularExpressions;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
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

    /// <summary>
    /// Renders a full 3D animated running Minecraft player model textured with real player skin.
    /// Features:
    /// - 16x Nearest-Neighbor upscaling for razor-sharp pixel art (zero blur in 3D).
    /// - Built-in Steve skin generator for cracked/offline players.
    /// - 6-part humanoid mesh: Head, Torso, Left Arm, Right Arm, Left Leg, Right Leg.
    /// - Animated running motion: forward sprinting lean, counter-swinging limbs, vertical stride bobbing.
    /// </summary>
    public class PlayerBody3D : Viewport3D
    {
        public static readonly DependencyProperty ProfileProperty = DependencyProperty.Register(
            nameof(Profile), typeof(object), typeof(PlayerBody3D), new PropertyMetadata(null, OnProfileChanged));

        public static readonly DependencyProperty IsRunningProperty = DependencyProperty.Register(
            nameof(IsRunning), typeof(bool), typeof(PlayerBody3D), new PropertyMetadata(true, OnIsRunningChanged));

        public static readonly DependencyProperty DisplayModeProperty = DependencyProperty.Register(
            nameof(DisplayMode), typeof(PlayerModelMode), typeof(PlayerBody3D), new PropertyMetadata(PlayerModelMode.FullBody, OnDisplayModeChanged));

        private static readonly HttpClient SkinClient = CreateSkinClient();
        private static readonly ConcurrentDictionary<string, Task<BitmapSource?>> SkinRequests = new(StringComparer.OrdinalIgnoreCase);
        private static readonly string SkinCacheDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Cache", "Skins");

        // Built-in 100% crisp Steve skin for cracked / offline players
        private static readonly BitmapSource SteveSkinSource = GenerateSteveSkin();

        // Transforms & Animation groups
        private readonly Model3DGroup _rootModelGroup = new();
        private readonly Model3DGroup _bodyGroup = new();
        private readonly Model3DGroup _headGroup = new();
        private readonly Model3DGroup _torsoGroup = new();
        private readonly Model3DGroup _rightArmGroup = new();
        private readonly Model3DGroup _leftArmGroup = new();
        private readonly Model3DGroup _rightLegGroup = new();
        private readonly Model3DGroup _leftLegGroup = new();

        // Limb Rotation Angles for Running Motion
        private readonly AxisAngleRotation3D _rightArmRotation = new(new Vector3D(1, 0, 0), 0);
        private readonly AxisAngleRotation3D _leftArmRotation = new(new Vector3D(1, 0, 0), 0);
        private readonly AxisAngleRotation3D _rightLegRotation = new(new Vector3D(1, 0, 0), 0);
        private readonly AxisAngleRotation3D _leftLegRotation = new(new Vector3D(1, 0, 0), 0);
        private readonly AxisAngleRotation3D _headNodRotation = new(new Vector3D(1, 0, 0), 4);
        private readonly TranslateTransform3D _bodyBounceTransform = new(0, 0, 0);

        // Overall Character Orientation (Heroic 3/4 Isometric Perspective)
        private readonly AxisAngleRotation3D _characterYawRotation = new(new Vector3D(0, 1, 0), -26);
        private readonly AxisAngleRotation3D _runningLeanRotation = new(new Vector3D(1, 0, 0), 10);

        private int _skinRequestId;
        private BitmapSource? _currentSkinSource;

        public PlayerBody3D()
        {
            ClipToBounds = true;
            BuildSceneHierarchy();
            ApplySkin(SteveSkinSource);

            IsVisibleChanged += (_, _) => UpdateAnimations();
            Loaded += (_, _) => UpdateAnimations();
            Unloaded += (_, _) => StopAnimations();
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

        private static void OnProfileChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
            ((PlayerBody3D)d).LoadSkinForProfile(e.NewValue);

        private static void OnIsRunningChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
            ((PlayerBody3D)d).UpdateAnimations();

        private static void OnDisplayModeChanged(DependencyObject d, DependencyPropertyChangedEventArgs e)
        {
            var control = (PlayerBody3D)d;
            control.RebuildCameraAndMeshes();
        }

        private void BuildSceneHierarchy()
        {
            UpdateCamera();

            var scene = new Model3DGroup();
            
            // Dual-tone lighting: ambient fill + crisp directional sunlight + purple cyber rim light
            scene.Children.Add(new AmbientLight(Color.FromRgb(175, 165, 205)));
            scene.Children.Add(new DirectionalLight(Color.FromRgb(255, 255, 255), new Vector3D(-0.4, -0.7, -1.0)));
            scene.Children.Add(new DirectionalLight(Color.FromRgb(168, 85, 247), new Vector3D(0.6, 0.4, -0.8)));

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
                // Tight camera framing on head only
                Camera = new PerspectiveCamera(
                    new Point3D(0.55, 1.55, 3.6),
                    new Vector3D(-0.55, -0.35, -3.6),
                    new Vector3D(0, 1, 0),
                    26);
            }
            else
            {
                // Full Body Running Camera
                Camera = new PerspectiveCamera(
                    new Point3D(0.65, 0.25, 4.4),
                    new Vector3D(-0.65, -0.25, -4.4),
                    new Vector3D(0, 1, 0),
                    38);
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
            bool isOffline = false;

            if (value is UserProfile profile)
            {
                username = profile.Username;
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

            if (string.IsNullOrWhiteSpace(username) || isOffline || username.Equals("Offline", StringComparison.OrdinalIgnoreCase) || username.Equals("Cracked", StringComparison.OrdinalIgnoreCase))
            {
                _currentSkinSource = SteveSkinSource;
                ApplySkin(SteveSkinSource);
                return;
            }

            // Immediately show Steve skin while loading so there is never a blank/grey box
            ApplySkin(SteveSkinSource);
            var requestId = ++_skinRequestId;
            var cacheKey = username.Trim();
            var skin = await SkinRequests.GetOrAdd(cacheKey, GetSkinAsync);
            
            if (requestId == _skinRequestId)
            {
                _currentSkinSource = skin ?? SteveSkinSource;
                ApplySkin(_currentSkinSource);
            }
        }

        private static async Task<BitmapSource?> GetSkinAsync(string username)
        {
            try
            {
                Directory.CreateDirectory(SkinCacheDirectory);
                var path = Path.Combine(SkinCacheDirectory, $"{username}.vayu-skin.png");
                var fromCache = File.Exists(path);
                byte[] bytes = fromCache
                    ? await File.ReadAllBytesAsync(path)
                    : await FetchSkinBytesAsync(username);

                if (bytes.Length == 0)
                {
                    return SteveSkinSource;
                }

                if (!fromCache)
                {
                    await File.WriteAllBytesAsync(path, bytes);
                }

                var rawBitmap = new BitmapImage();
                rawBitmap.BeginInit();
                rawBitmap.CacheOption = BitmapCacheOption.OnLoad;
                rawBitmap.StreamSource = new MemoryStream(bytes);
                rawBitmap.EndInit();
                rawBitmap.Freeze();

                // 16x Nearest-Neighbor upscaling to eliminate 3D blurriness completely
                return UpscaleNearestNeighbor(rawBitmap, 16);
            }
            catch
            {
                return SteveSkinSource;
            }
        }

        private static async Task<byte[]> FetchSkinBytesAsync(string username)
        {
            try
            {
                // Try NameMC profile skin scraping
                var profileHtml = await SkinClient.GetStringAsync($"https://namemc.com/profile/{Uri.EscapeDataString(username)}");
                var match = Regex.Match(profileHtml, @"(?:s(?:\\)?\.namemc(?:\\)?\.com(?:\\)?/i|/skin)(?:\\)?/([a-f0-9]{16})", RegexOptions.IgnoreCase);
                if (match.Success)
                {
                    return await SkinClient.GetByteArrayAsync($"https://s.namemc.com/i/{match.Groups[1].Value}.png");
                }
            }
            catch { }

            // Fallback to Minotar skin endpoint
            try
            {
                var bytes = await SkinClient.GetByteArrayAsync($"https://minotar.net/skin/{Uri.EscapeDataString(username)}");
                if (bytes != null && bytes.Length > 200)
                {
                    return bytes;
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
        /// Upscales a 64x64 / 64x32 skin bitmap by factor (e.g. 16x to 1024x1024)
        /// using pure Nearest-Neighbor pixel replication. This ensures every Minecraft pixel is
        /// crystal clear and 100% sharp in WPF 3D Viewport.
        /// </summary>
        private static BitmapSource UpscaleNearestNeighbor(BitmapSource source, int scale = 16)
        {
            try
            {
                int srcWidth = source.PixelWidth;
                int srcHeight = source.PixelHeight;
                int dstWidth = srcWidth * scale;
                int dstHeight = srcHeight * scale;

                var format = PixelFormats.Bgra32;
                var converted = new FormatConvertedBitmap(source, format, null, 0);
                int srcStride = srcWidth * 4;
                var srcPixels = new byte[srcStride * srcHeight];
                converted.CopyPixels(srcPixels, srcStride, 0);

                int dstStride = dstWidth * 4;
                var dstPixels = new byte[dstStride * dstHeight];

                for (int y = 0; y < dstHeight; y++)
                {
                    int srcY = y / scale;
                    int srcRowOffset = srcY * srcStride;
                    int dstRowOffset = y * dstStride;

                    for (int x = 0; x < dstWidth; x++)
                    {
                        int srcX = x / scale;
                        int srcPixelOffset = srcRowOffset + (srcX * 4);
                        int dstPixelOffset = dstRowOffset + (x * 4);

                        dstPixels[dstPixelOffset + 0] = srcPixels[srcPixelOffset + 0]; // B
                        dstPixels[dstPixelOffset + 1] = srcPixels[srcPixelOffset + 1]; // G
                        dstPixels[dstPixelOffset + 2] = srcPixels[srcPixelOffset + 2]; // R
                        dstPixels[dstPixelOffset + 3] = srcPixels[srcPixelOffset + 3]; // A
                    }
                }

                var result = BitmapSource.Create(dstWidth, dstHeight, 96, 96, format, null, dstPixels, dstStride);
                result.Freeze();
                return result;
            }
            catch
            {
                return source;
            }
        }

        /// <summary>
        /// Programmatically creates the official 64x64 Minecraft Steve skin upscaled for 100% crisp rendering.
        /// </summary>
        private static BitmapSource GenerateSteveSkin()
        {
            int w = 64;
            int h = 64;
            var pixels = new uint[w * h];

            // Color Palette
            uint hairDark = 0xFF2B170B;
            uint hairMid = 0xFF452817;
            uint hairLight = 0xFF54331E;
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
            uint shoeDark = 0xFF363636;

            void FillRect(int rx, int ry, int rw, int rh, uint color)
            {
                for (int y = ry; y < ry + rh && y < h; y++)
                    for (int x = rx; x < rx + rw && x < w; x++)
                        pixels[y * w + x] = color;
            }

            // 1. Head Top (8..15, 0..7)
            FillRect(8, 0, 8, 8, hairMid);
            // 2. Head Bottom (16..23, 0..7)
            FillRect(16, 0, 8, 8, skinShade);

            // 3. Head Faces (y: 8..15)
            // Head Right (0..7, 8..15)
            FillRect(0, 8, 8, 8, skinBase);
            FillRect(0, 8, 8, 3, hairMid);
            FillRect(0, 11, 2, 2, hairDark);

            // Head Front (8..15, 8..15)
            FillRect(8, 8, 8, 8, skinBase);
            FillRect(8, 8, 8, 3, hairMid);
            FillRect(8, 10, 1, 1, hairDark);
            FillRect(15, 10, 1, 1, hairDark);
            // Eyes
            pixels[12 * w + 9] = eyeWhite;
            pixels[12 * w + 10] = eyeBlue;
            pixels[12 * w + 13] = eyeBlue;
            pixels[12 * w + 14] = eyeWhite;
            // Nose & Mouth / Beard
            FillRect(11, 13, 2, 1, skinShade);
            FillRect(10, 14, 4, 1, beard);
            pixels[13 * w + 11] = beard;
            pixels[13 * w + 12] = beard;

            // Head Left (16..23, 8..15)
            FillRect(16, 8, 8, 8, skinBase);
            FillRect(16, 8, 8, 3, hairMid);
            FillRect(22, 11, 2, 2, hairDark);

            // Head Back (24..31, 8..15)
            FillRect(24, 8, 8, 8, hairMid);
            FillRect(24, 8, 8, 4, hairDark);

            // 4. Torso (y: 16..31)
            FillRect(20, 16, 8, 4, shirtShade);
            FillRect(28, 16, 8, 4, shirtShade);
            FillRect(16, 20, 24, 12, shirtTeal);
            // Torso front neck cutout
            FillRect(22, 20, 4, 2, skinBase);
            FillRect(23, 21, 2, 1, skinShade);

            // 5. Right Arm (x: 40..55, y: 16..31)
            FillRect(44, 16, 8, 4, shirtShade);
            FillRect(40, 20, 16, 4, shirtTeal);
            FillRect(40, 24, 16, 8, skinBase);

            // 6. Left Arm (x: 32..47, y: 48..63)
            FillRect(36, 48, 8, 4, shirtShade);
            FillRect(32, 52, 16, 4, shirtTeal);
            FillRect(32, 56, 16, 8, skinBase);

            // 7. Right Leg (x: 0..15, y: 16..31)
            FillRect(4, 16, 8, 4, pantsShade);
            FillRect(0, 20, 16, 8, pantsBlue);
            FillRect(0, 28, 16, 4, shoeGray);

            // 8. Left Leg (x: 16..31, y: 48..63)
            FillRect(20, 48, 8, 4, pantsShade);
            FillRect(16, 52, 16, 8, pantsBlue);
            FillRect(16, 60, 16, 4, shoeGray);

            var rawSteve = BitmapSource.Create(w, h, 96, 96, PixelFormats.Bgra32, null, pixels, w * 4);
            rawSteve.Freeze();
            return UpscaleNearestNeighbor(rawSteve, 16);
        }

        private void ClearAllMeshes()
        {
            _headGroup.Children.Clear();
            _torsoGroup.Children.Clear();
            _rightArmGroup.Children.Clear();
            _leftArmGroup.Children.Clear();
            _rightLegGroup.Children.Clear();
            _leftLegGroup.Children.Clear();
        }

        private void ApplySkin(BitmapSource skin)
        {
            ClearAllMeshes();

            int scale = skin.PixelWidth / 64;
            if (scale < 1) scale = 1;

            Int32Rect S(int x, int y, int tw, int th) => new(x * scale, y * scale, tw * scale, th * scale);

            bool is64x64 = skin.PixelHeight >= 64 * scale;

            // 1. Head (8x8x8) centered at X=0, Y=1.2 to 2.0, Z=-0.4 to 0.4
            AddCuboid(_headGroup, skin,
                -0.4, 1.2, -0.4, 0.4, 2.0, 0.4,
                frontTile: S(8, 8, 8, 8),
                backTile: S(24, 8, 8, 8),
                rightTile: S(0, 8, 8, 8),
                leftTile: S(16, 8, 8, 8),
                topTile: S(8, 0, 8, 8),
                bottomTile: S(16, 0, 8, 8));

            if (DisplayMode == PlayerModelMode.HeadOnly)
                return;

            // 2. Torso (8x12x4) centered at X=0, Y=0.0 to 1.2, Z=-0.2 to 0.2
            AddCuboid(_torsoGroup, skin,
                -0.4, 0.0, -0.2, 0.4, 1.2, 0.2,
                frontTile: S(20, 20, 8, 12),
                backTile: S(32, 20, 8, 12),
                rightTile: S(16, 20, 4, 12),
                leftTile: S(28, 20, 4, 12),
                topTile: S(20, 16, 8, 4),
                bottomTile: S(28, 16, 8, 4));

            // 3. Right Arm (4x12x4) at X=-0.8 to -0.4, Y=0.0 to 1.2, Z=-0.2 to 0.2
            AddCuboid(_rightArmGroup, skin,
                -0.8, 0.0, -0.2, -0.4, 1.2, 0.2,
                frontTile: S(44, 20, 4, 12),
                backTile: S(52, 20, 4, 12),
                rightTile: S(40, 20, 4, 12),
                leftTile: S(48, 20, 4, 12),
                topTile: S(44, 16, 4, 4),
                bottomTile: S(48, 16, 4, 4));

            // 4. Left Arm (4x12x4) at X=0.4 to 0.8, Y=0.0 to 1.2, Z=-0.2 to 0.2
            if (is64x64)
            {
                AddCuboid(_leftArmGroup, skin,
                    0.4, 0.0, -0.2, 0.8, 1.2, 0.2,
                    frontTile: S(36, 52, 4, 12),
                    backTile: S(44, 52, 4, 12),
                    rightTile: S(32, 52, 4, 12),
                    leftTile: S(40, 52, 4, 12),
                    topTile: S(36, 48, 4, 4),
                    bottomTile: S(40, 48, 4, 4));
            }
            else
            {
                // Mirrored Right Arm for legacy skins
                AddCuboid(_leftArmGroup, skin,
                    0.4, 0.0, -0.2, 0.8, 1.2, 0.2,
                    frontTile: S(44, 20, 4, 12),
                    backTile: S(52, 20, 4, 12),
                    rightTile: S(48, 20, 4, 12),
                    leftTile: S(40, 20, 4, 12),
                    topTile: S(44, 16, 4, 4),
                    bottomTile: S(48, 16, 4, 4));
            }

            // 5. Right Leg (4x12x4) at X=-0.4 to 0.0, Y=-1.2 to 0.0, Z=-0.2 to 0.2
            AddCuboid(_rightLegGroup, skin,
                -0.4, -1.2, -0.2, 0.0, 0.0, 0.2,
                frontTile: S(4, 20, 4, 12),
                backTile: S(12, 20, 4, 12),
                rightTile: S(0, 20, 4, 12),
                leftTile: S(8, 20, 4, 12),
                topTile: S(4, 16, 4, 4),
                bottomTile: S(8, 16, 4, 4));

            // 6. Left Leg (4x12x4) at X=0.0 to 0.4, Y=-1.2 to 0.0, Z=-0.2 to 0.2
            if (is64x64)
            {
                AddCuboid(_leftLegGroup, skin,
                    0.0, -1.2, -0.2, 0.4, 0.0, 0.2,
                    frontTile: S(20, 52, 4, 12),
                    backTile: S(28, 52, 4, 12),
                    rightTile: S(16, 52, 4, 12),
                    leftTile: S(24, 52, 4, 12),
                    topTile: S(20, 48, 4, 4),
                    bottomTile: S(24, 48, 4, 4));
            }
            else
            {
                // Mirrored Right Leg for legacy skins
                AddCuboid(_leftLegGroup, skin,
                    0.0, -1.2, -0.2, 0.4, 0.0, 0.2,
                    frontTile: S(4, 20, 4, 12),
                    backTile: S(12, 20, 4, 12),
                    rightTile: S(8, 20, 4, 12),
                    leftTile: S(0, 20, 4, 12),
                    topTile: S(4, 16, 4, 4),
                    bottomTile: S(8, 16, 4, 4));
            }
        }

        private void AddCuboid(Model3DGroup parentGroup, BitmapSource skin,
            double x1, double y1, double z1, double x2, double y2, double z2,
            Int32Rect frontTile, Int32Rect backTile,
            Int32Rect rightTile, Int32Rect leftTile,
            Int32Rect topTile, Int32Rect bottomTile)
        {
            // Front (+Z)
            AddTexturedFace(parentGroup, skin, frontTile,
                new Point3D(x1, y1, z2), new Point3D(x2, y1, z2),
                new Point3D(x2, y2, z2), new Point3D(x1, y2, z2));

            // Back (-Z)
            AddTexturedFace(parentGroup, skin, backTile,
                new Point3D(x2, y1, z1), new Point3D(x1, y1, z1),
                new Point3D(x1, y2, z1), new Point3D(x2, y2, z1));

            // Right (-X)
            AddTexturedFace(parentGroup, skin, rightTile,
                new Point3D(x1, y1, z1), new Point3D(x1, y1, z2),
                new Point3D(x1, y2, z2), new Point3D(x1, y2, z1));

            // Left (+X)
            AddTexturedFace(parentGroup, skin, leftTile,
                new Point3D(x2, y1, z2), new Point3D(x2, y1, z1),
                new Point3D(x2, y2, z1), new Point3D(x2, y2, z2));

            // Top (+Y)
            AddTexturedFace(parentGroup, skin, topTile,
                new Point3D(x1, y2, z2), new Point3D(x2, y2, z2),
                new Point3D(x2, y2, z1), new Point3D(x1, y2, z1));

            // Bottom (-Y)
            AddTexturedFace(parentGroup, skin, bottomTile,
                new Point3D(x1, y1, z1), new Point3D(x2, y1, z1),
                new Point3D(x2, y1, z2), new Point3D(x1, y1, z2));
        }

        private void AddTexturedFace(Model3DGroup group, BitmapSource skin, Int32Rect tile,
            Point3D a, Point3D b, Point3D c, Point3D d)
        {
            try
            {
                if (tile.X + tile.Width > skin.PixelWidth || tile.Y + tile.Height > skin.PixelHeight)
                    return;

                var cropped = new CroppedBitmap(skin, tile);
                cropped.Freeze();
                var brush = new ImageBrush(cropped) { Stretch = Stretch.Fill };
                RenderOptions.SetBitmapScalingMode(brush, BitmapScalingMode.NearestNeighbor);
                var material = new DiffuseMaterial(brush);

                var mesh = new MeshGeometry3D();
                mesh.Positions.Add(a); mesh.Positions.Add(b); mesh.Positions.Add(c); mesh.Positions.Add(d);
                mesh.TextureCoordinates.Add(new Point(0, 1));
                mesh.TextureCoordinates.Add(new Point(1, 1));
                mesh.TextureCoordinates.Add(new Point(1, 0));
                mesh.TextureCoordinates.Add(new Point(0, 0));
                mesh.TriangleIndices.Add(0); mesh.TriangleIndices.Add(1); mesh.TriangleIndices.Add(2);
                mesh.TriangleIndices.Add(0); mesh.TriangleIndices.Add(2); mesh.TriangleIndices.Add(3);

                group.Children.Add(new GeometryModel3D(mesh, material) { BackMaterial = material });
            }
            catch { }
        }

        private void UpdateAnimations()
        {
            StopAnimations();

            if (!IsLoaded || !IsVisible || !IsRunning)
                return;

            var runningCycleDuration = TimeSpan.FromSeconds(0.68);
            var sineEase = new SineEase { EasingMode = EasingMode.EaseInOut };

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

            // 6. Character Subtle Yaw Drift (-32° to -20°)
            var yawAnim = new DoubleAnimation(-32, -20, TimeSpan.FromSeconds(3.5))
            {
                AutoReverse = true,
                RepeatBehavior = RepeatBehavior.Forever,
                EasingFunction = sineEase
            };
            _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, yawAnim);
        }

        private void StopAnimations()
        {
            _rightArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _leftArmRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _rightLegRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _leftLegRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
            _bodyBounceTransform.BeginAnimation(TranslateTransform3D.OffsetYProperty, null);
            _characterYawRotation.BeginAnimation(AxisAngleRotation3D.AngleProperty, null);
        }
    }
}
