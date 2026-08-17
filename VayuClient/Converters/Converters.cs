using System;
using System.Collections.Concurrent;
using System.Globalization;
using System.IO;
using System.Net.Http;
using System.Security.Cryptography;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Data;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using VayuClient.Models;

namespace VayuClient.Converters
{
    public class BoolToVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
            => value is true ? Visibility.Visible : Visibility.Collapsed;

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => value is Visibility.Visible;
    }

    public class BoolToAccentBrushConverter : IValueConverter
    {
        private static readonly SolidColorBrush ActiveBrush = new(Color.FromRgb(0x3B, 0x82, 0xF6));
        private static readonly SolidColorBrush InactiveBrush = new(Color.FromRgb(0x64, 0x74, 0x8B));

        static BoolToAccentBrushConverter()
        {
            ActiveBrush.Freeze();
            InactiveBrush.Freeze();
        }

        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
            => value is true ? ActiveBrush : InactiveBrush;

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => throw new NotImplementedException();
    }

    public class InverseBoolConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
            => value is bool b ? !b : value;

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => value is bool b ? !b : value;
    }

    public class InverseBoolToVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
            => value is true ? Visibility.Collapsed : Visibility.Visible;

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => value is Visibility.Collapsed;
    }

    public class AccountTypeToLabelConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
            => value is AccountType at ? at switch
            {
                AccountType.Microsoft => "MICROSOFT ACCOUNT",
                AccountType.Offline => "OFFLINE PROFILE",
                _ => "UNKNOWN"
            } : "UNKNOWN";

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => throw new NotImplementedException();
    }

    public class NullToVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
            => value != null ? Visibility.Visible : Visibility.Collapsed;

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => throw new NotImplementedException();
    }

    public class EqualValueToVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            if (value == null && parameter == null) return Visibility.Visible;
            if (value == null || parameter == null) return Visibility.Collapsed;
            return string.Equals(value.ToString(), parameter.ToString(), StringComparison.OrdinalIgnoreCase)
                ? Visibility.Visible
                : Visibility.Collapsed;
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => throw new NotImplementedException();
    }

    public class EqualValueToBoolConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            if (value == null && parameter == null) return true;
            if (value == null || parameter == null) return false;
            return string.Equals(value.ToString(), parameter.ToString(), StringComparison.OrdinalIgnoreCase);
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => throw new NotImplementedException();
    }

    public class StringToInitialConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            if (value is string s && !string.IsNullOrWhiteSpace(s))
            {
                return s.Trim()[0].ToString().ToUpperInvariant();
            }
            return "V";
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => throw new NotImplementedException();
    }

    public class SkinHeadImageConverter : IValueConverter
    {
        private static readonly System.Windows.Media.Imaging.BitmapImage DefaultSteveHead;
        private static readonly System.Collections.Concurrent.ConcurrentDictionary<string, System.Windows.Media.Imaging.BitmapImage> _avatarCache = new(StringComparer.OrdinalIgnoreCase);
        private static readonly System.Net.Http.HttpClient _httpClient = new() { Timeout = TimeSpan.FromSeconds(8) };
        private static readonly string _skinCacheDir;

        static SkinHeadImageConverter()
        {
            DefaultSteveHead = new System.Windows.Media.Imaging.BitmapImage();
            DefaultSteveHead.BeginInit();
            DefaultSteveHead.UriSource = new Uri("pack://application:,,,/Assets/Images/steve_head.png", UriKind.Absolute);
            DefaultSteveHead.CacheOption = System.Windows.Media.Imaging.BitmapCacheOption.OnLoad;
            DefaultSteveHead.EndInit();
            DefaultSteveHead.Freeze();

            _skinCacheDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Cache", "Skins");
            try { Directory.CreateDirectory(_skinCacheDir); } catch { }

            if (!_httpClient.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _httpClient.DefaultRequestHeaders.Add("User-Agent", "VayuClient/1.2.2 (ANSH9BOSS)");
            }
        }

        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            string? username = null;
            if (value is UserProfile profile)
            {
                username = profile.Username;
            }
            else if (value is string text)
            {
                username = text;
            }

            if (string.IsNullOrWhiteSpace(username))
            {
                return DefaultSteveHead;
            }

            return GetSkinHeadForUsername(username);
        }

        private static System.Windows.Media.Imaging.BitmapImage GetSkinHeadForUsername(string username)
        {
            var cleanUser = username.Trim();
            if (string.IsNullOrWhiteSpace(cleanUser))
            {
                return DefaultSteveHead;
            }

            if (_avatarCache.TryGetValue(cleanUser, out var cachedBmp))
            {
                return cachedBmp;
            }

            string localFile = Path.Combine(_skinCacheDir, $"{cleanUser}.png");
            if (File.Exists(localFile))
            {
                try
                {
                    var bmp = LoadBitmapFromFile(localFile);
                    if (bmp != null)
                    {
                        _avatarCache[cleanUser] = bmp;
                        return bmp;
                    }
                }
                catch { }
            }

            // Asynchronously fetch skin head from Minotar
            _ = Task.Run(async () =>
            {
                try
                {
                    string url = $"https://minotar.net/helm/{Uri.EscapeDataString(cleanUser)}/64.png";
                    var data = await _httpClient.GetByteArrayAsync(url);
                    if (data != null && data.Length > 0)
                    {
                        await File.WriteAllBytesAsync(localFile, data);
                        var loaded = LoadBitmapFromBytes(data);
                        if (loaded != null)
                        {
                            _avatarCache[cleanUser] = loaded;
                        }
                    }
                }
                catch { }
            });

            // Return Steve head while downloading or if fallback needed
            return DefaultSteveHead;
        }

        private static System.Windows.Media.Imaging.BitmapImage? LoadBitmapFromFile(string path)
        {
            try
            {
                var bytes = File.ReadAllBytes(path);
                return LoadBitmapFromBytes(bytes);
            }
            catch
            {
                return null;
            }
        }

        private static System.Windows.Media.Imaging.BitmapImage? LoadBitmapFromBytes(byte[] bytes)
        {
            try
            {
                using var ms = new MemoryStream(bytes);
                var bmp = new System.Windows.Media.Imaging.BitmapImage();
                bmp.BeginInit();
                bmp.CacheOption = System.Windows.Media.Imaging.BitmapCacheOption.OnLoad;
                bmp.StreamSource = ms;
                bmp.EndInit();
                bmp.Freeze();
                return bmp;
            }
            catch
            {
                return null;
            }
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => throw new NotImplementedException();
    }

    public class BoolToOnlineStatusBrushConverter : IValueConverter
    {
        private static readonly System.Windows.Media.SolidColorBrush OnlineBrush = new(System.Windows.Media.Color.FromRgb(34, 197, 94));
        private static readonly System.Windows.Media.SolidColorBrush OfflineBrush = new(System.Windows.Media.Color.FromRgb(100, 116, 139));

        static BoolToOnlineStatusBrushConverter()
        {
            OnlineBrush.Freeze();
            OfflineBrush.Freeze();
        }

        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
            => value is true ? OnlineBrush : OfflineBrush;

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => throw new NotImplementedException();
    }

    public class WebImageConverter : IValueConverter
    {
        private static readonly System.Collections.Concurrent.ConcurrentDictionary<string, System.Windows.Media.Imaging.BitmapImage> _imageCache = new(StringComparer.OrdinalIgnoreCase);
        private static readonly System.Net.Http.HttpClient _httpClient = new() { Timeout = TimeSpan.FromSeconds(10) };
        private static readonly string _webImageCacheDir;

        static WebImageConverter()
        {
            _webImageCacheDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "VayuClient", "Cache", "WebImages");
            try { Directory.CreateDirectory(_webImageCacheDir); } catch { }

            if (!_httpClient.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _httpClient.DefaultRequestHeaders.Add("User-Agent", "VayuClient/1.2.2 (ANSH9BOSS)");
            }
        }

        public object? Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            if (value is not string url || string.IsNullOrWhiteSpace(url))
            {
                return null;
            }

            url = url.Trim();

            // 1. Direct local file support
            if (File.Exists(url))
            {
                if (_imageCache.TryGetValue(url, out var localCached))
                {
                    return localCached;
                }
                var localBmp = LoadBitmapFromFile(url);
                if (localBmp != null)
                {
                    _imageCache[url] = localBmp;
                    return localBmp;
                }
            }

            // 2. Pack URI or resource
            if (url.StartsWith("pack://", StringComparison.OrdinalIgnoreCase) || url.StartsWith("/", StringComparison.OrdinalIgnoreCase))
            {
                try
                {
                    var uri = new Uri(url, UriKind.RelativeOrAbsolute);
                    var bmp = new System.Windows.Media.Imaging.BitmapImage(uri);
                    bmp.Freeze();
                    return bmp;
                }
                catch { }
            }

            // 3. Web URL
            if (!url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) && !url.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
            {
                return null;
            }

            if (_imageCache.TryGetValue(url, out var cached))
            {
                return cached;
            }

            // Local cache file by hash
            string hash = System.Convert.ToHexString(SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(url)));
            string localFile = Path.Combine(_webImageCacheDir, $"{hash}.png");

            if (File.Exists(localFile))
            {
                try
                {
                    var bmp = LoadBitmapFromFile(localFile);
                    if (bmp != null)
                    {
                        _imageCache[url] = bmp;
                        return bmp;
                    }
                }
                catch { }
            }

            // Asynchronously fetch image from CDN
            _ = Task.Run(async () =>
            {
                try
                {
                    var data = await _httpClient.GetByteArrayAsync(url);
                    if (data != null && data.Length > 0)
                    {
                        await File.WriteAllBytesAsync(localFile, data);
                        var loaded = LoadBitmapFromBytes(data);
                        if (loaded != null)
                        {
                            _imageCache[url] = loaded;
                        }
                    }
                }
                catch { }
            });

            return null;
        }

        private static System.Windows.Media.Imaging.BitmapImage? LoadBitmapFromFile(string path)
        {
            try
            {
                var bytes = File.ReadAllBytes(path);
                return LoadBitmapFromBytes(bytes);
            }
            catch
            {
                return null;
            }
        }

        private static System.Windows.Media.Imaging.BitmapImage? LoadBitmapFromBytes(byte[] bytes)
        {
            try
            {
                using var ms = new MemoryStream(bytes);
                var bmp = new System.Windows.Media.Imaging.BitmapImage();
                bmp.BeginInit();
                bmp.CacheOption = System.Windows.Media.Imaging.BitmapCacheOption.OnLoad;
                bmp.StreamSource = ms;
                bmp.EndInit();
                bmp.Freeze();
                return bmp;
            }
            catch
            {
                return null;
            }
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
            => throw new NotImplementedException();
    }

    public class ZeroToVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            if (value is int i && i == 0) return Visibility.Visible;
            if (value == null) return Visibility.Visible;
            return Visibility.Collapsed;
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class NonZeroToVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            if (value is int i && i > 0) return Visibility.Visible;
            return Visibility.Collapsed;
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class PingToColorBrushConverter : IValueConverter
    {
        private static readonly SolidColorBrush GreenBrush = new(Color.FromRgb(0x4A, 0xDE, 0x80));
        private static readonly SolidColorBrush YellowBrush = new(Color.FromRgb(0xFA, 0xCC, 0x15));
        private static readonly SolidColorBrush RedBrush = new(Color.FromRgb(0xEF, 0x44, 0x44));
        private static readonly SolidColorBrush GrayBrush = new(Color.FromRgb(0x94, 0xA3, 0xB8));

        static PingToColorBrushConverter()
        {
            GreenBrush.Freeze();
            YellowBrush.Freeze();
            RedBrush.Freeze();
            GrayBrush.Freeze();
        }

        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            if (value is int ms)
            {
                if (ms < 0) return GrayBrush;
                if (ms < 80) return GreenBrush;
                if (ms < 160) return YellowBrush;
                return RedBrush;
            }
            return GrayBrush;
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) => throw new NotImplementedException();
    }

    public class BoolToStringConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
        {
            string param = parameter?.ToString() ?? "True|False";
            var parts = param.Split('|');
            string trueStr = parts.Length > 0 ? parts[0] : "True";
            string falseStr = parts.Length > 1 ? parts[1] : "False";
            return value is true ? trueStr : falseStr;
        }

        public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) => throw new NotImplementedException();
    }
}

