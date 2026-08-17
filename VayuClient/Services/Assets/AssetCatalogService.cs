using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Windows.Media.Imaging;

namespace VayuClient.Services.Assets
{
    public record BackgroundTheme(string Id, string Title, string Category, string ResourcePath, string AccentColor);

    public interface IAssetCatalogService
    {
        IReadOnlyList<BackgroundTheme> GetAllThemes();
        BackgroundTheme GetThemeForInstance(string instanceName, string loader, string? modpackId, string? profile);
        BackgroundTheme GetThemeForPage(string pageKey);
        BackgroundTheme GetRandomTheme();
        BitmapImage? GetCachedThumbnail(string resourcePath, int decodeWidth = 320);
    }

    public class AssetCatalogService : IAssetCatalogService
    {
        private static readonly List<BackgroundTheme> _themes = new()
        {
            new("hero", "Vayu Hero Core", "Default", "/Assets/Images/vayu_minecraft_hero.jpg", "#38BDF8"),
            new("pvp", "PvP Battle Arena", "Competitive", "/Assets/Images/bg_pvp_arena.jpg", "#EF4444"),
            new("nether", "Cyber Nether Fortress", "Installations", "/Assets/Images/bg_cyber_nether.jpg", "#F97316"),
            new("lush", "Lush Caves Bioluminescence", "Survival", "/Assets/Images/bg_lush_caves.jpg", "#10B981"),
            new("aurora", "Mountain Aurora Skyline", "High FPS", "/Assets/Images/bg_mountain_aurora.jpg", "#06B6D4"),
            new("cherry", "Cherry Grove Haven", "Modpacks", "/Assets/Images/bg_cherry_grove.jpg", "#EC4899"),
            new("ocean", "Ocean Monument Depths", "Exploration", "/Assets/Images/bg_ocean_monument.jpg", "#3B82F6"),
            new("fantasy", "Fantasy Sky Islands", "Servers", "/Assets/Images/bg_fantasy_islands.jpg", "#8B5CF6")
        };

        private readonly Dictionary<string, WeakReference<BitmapImage>> _thumbCache = new();
        private readonly Random _random = new();

        public IReadOnlyList<BackgroundTheme> GetAllThemes() => _themes;

        public BackgroundTheme GetThemeForInstance(string instanceName, string loader, string? modpackId, string? profile)
        {
            var combined = $"{instanceName} {loader} {modpackId} {profile}".ToLowerInvariant();
            if (combined.Contains("pvp") || combined.Contains("combat") || combined.Contains("bedwars") || combined.Contains("competitive"))
                return _themes.First(t => t.Id == "pvp");
            if (combined.Contains("survival") || combined.Contains("hardcore") || combined.Contains("lush") || combined.Contains("cave"))
                return _themes.First(t => t.Id == "lush");
            if (combined.Contains("fps") || combined.Contains("opti") || combined.Contains("sodium") || combined.Contains("speed"))
                return _themes.First(t => t.Id == "aurora");
            if (combined.Contains("mod") || combined.Contains("forge") || combined.Contains("pack") || combined.Contains("tech"))
                return _themes.First(t => t.Id == "cherry");
            if (combined.Contains("ocean") || combined.Contains("water") || combined.Contains("abyss"))
                return _themes.First(t => t.Id == "ocean");
            if (combined.Contains("sky") || combined.Contains("island") || combined.Contains("server"))
                return _themes.First(t => t.Id == "fantasy");
            if (combined.Contains("nether") || combined.Contains("fire") || combined.Contains("red"))
                return _themes.First(t => t.Id == "nether");

            return _themes.First(t => t.Id == "hero");
        }

        public BackgroundTheme GetThemeForPage(string pageKey)
        {
            return pageKey.ToLowerInvariant() switch
            {
                "servers" => _themes.First(t => t.Id == "fantasy"),
                "mods" => _themes.First(t => t.Id == "lush"),
                "installations" or "installationmanager" or "versions" => _themes.First(t => t.Id == "nether"),
                "settings" => _themes.First(t => t.Id == "aurora"),
                "accounts" => _themes.First(t => t.Id == "ocean"),
                _ => _themes.First(t => t.Id == "hero")
            };
        }

        public BackgroundTheme GetRandomTheme()
        {
            lock (_random)
            {
                return _themes[_random.Next(_themes.Count)];
            }
        }

        public BitmapImage? GetCachedThumbnail(string resourcePath, int decodeWidth = 320)
        {
            if (string.IsNullOrEmpty(resourcePath)) return null;

            lock (_thumbCache)
            {
                if (_thumbCache.TryGetValue(resourcePath, out var weakRef) && weakRef.TryGetTarget(out var cachedImage))
                {
                    return cachedImage;
                }

                try
                {
                    var uri = resourcePath.StartsWith("pack://") || resourcePath.StartsWith("/")
                        ? new Uri(resourcePath, UriKind.RelativeOrAbsolute)
                        : new Uri(resourcePath, UriKind.Absolute);

                    var img = new BitmapImage();
                    img.BeginInit();
                    img.UriSource = uri;
                    img.DecodePixelWidth = decodeWidth;
                    img.CacheOption = BitmapCacheOption.OnLoad;
                    img.EndInit();
                    img.Freeze();

                    _thumbCache[resourcePath] = new WeakReference<BitmapImage>(img);
                    return img;
                }
                catch
                {
                    return null;
                }
            }
        }
    }
}
