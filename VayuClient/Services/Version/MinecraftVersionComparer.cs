using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;
using VayuClient.Models;

namespace VayuClient.Services.Version
{
    /// <summary>
    /// Robust Minecraft-version-aware comparer handling legacy 1.x.x, modern 26.x+, snapshots, and release dates.
    /// </summary>
    public class MinecraftVersionComparer : IComparer<MinecraftVersion>, IComparer<string>
    {
        public static readonly MinecraftVersionComparer Instance = new();

        public int Compare(MinecraftVersion? x, MinecraftVersion? y)
        {
            if (ReferenceEquals(x, y)) return 0;
            if (x == null) return -1;
            if (y == null) return 1;

            // 1. ReleaseDate comparison if available and different
            if (x.ReleaseDate != DateTime.MinValue && y.ReleaseDate != DateTime.MinValue)
            {
                int dateComp = DateTime.Compare(x.ReleaseDate, y.ReleaseDate);
                if (dateComp != 0) return dateComp;
            }

            // 2. Version ID String comparison
            return CompareVersionStrings(x.Id, y.Id);
        }

        public int Compare(string? x, string? y) => CompareVersionStrings(x, y);

        public static int CompareVersionStrings(string? xStr, string? yStr)
        {
            if (string.Equals(xStr, yStr, StringComparison.OrdinalIgnoreCase)) return 0;
            if (string.IsNullOrEmpty(xStr)) return -1;
            if (string.IsNullOrEmpty(yStr)) return 1;

            var xTokens = TokenizeVersion(xStr);
            var yTokens = TokenizeVersion(yStr);

            int minLen = Math.Min(xTokens.Count, yTokens.Count);
            for (int i = 0; i < minLen; i++)
            {
                var xTok = xTokens[i];
                var yTok = yTokens[i];

                if (xTok.IsNumber && yTok.IsNumber)
                {
                    if (xTok.NumberValue != yTok.NumberValue)
                        return xTok.NumberValue.CompareTo(yTok.NumberValue);
                }
                else
                {
                    int textComp = string.Compare(xTok.TextValue, yTok.TextValue, StringComparison.OrdinalIgnoreCase);
                    if (textComp != 0) return textComp;
                }
            }

            return xTokens.Count.CompareTo(yTokens.Count);
        }

        private static List<VersionToken> TokenizeVersion(string version)
        {
            var tokens = new List<VersionToken>();
            var matches = Regex.Matches(version, @"\d+|[a-zA-Z]+");
            foreach (Match match in matches)
            {
                if (long.TryParse(match.Value, out var num))
                {
                    tokens.Add(new VersionToken { IsNumber = true, NumberValue = num });
                }
                else
                {
                    tokens.Add(new VersionToken { IsNumber = false, TextValue = match.Value ?? "" });
                }
            }
            return tokens;
        }

        private struct VersionToken
        {
            public bool IsNumber;
            public long NumberValue;
            public string TextValue;
        }
    }
}
