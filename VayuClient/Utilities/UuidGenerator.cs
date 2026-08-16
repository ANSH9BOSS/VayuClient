using System.Security.Cryptography;
using System.Text;

namespace VayuClient.Utilities
{
    /// <summary>
    /// Generates deterministic offline UUIDs from usernames (Java-style UUID v3).
    /// </summary>
    public static class UuidGenerator
    {
        public static string GenerateOfflineUuid(string username)
        {
            var data = Encoding.UTF8.GetBytes("OfflinePlayer:" + username);
            var hash = MD5.HashData(data);

            // Set version to 3 (name-based)
            hash[6] = (byte)((hash[6] & 0x0F) | 0x30);
            // Set variant to RFC 4122
            hash[8] = (byte)((hash[8] & 0x3F) | 0x80);

            var hex = BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
            return $"{hex[..8]}-{hex[8..12]}-{hex[12..16]}-{hex[16..20]}-{hex[20..]}";
        }
    }
}
