using System;
using CommunityToolkit.Mvvm.ComponentModel;
using Newtonsoft.Json;

namespace VayuClient.Models
{
    /// <summary>
    /// Represents a user profile in VayuClient.
    /// Can be either a Microsoft-authenticated account or a local offline profile.
    /// </summary>
    public partial class UserProfile : ObservableObject
    {
        [JsonProperty("id")]
        public string Id { get; set; } = string.Empty;

        [ObservableProperty]
        [JsonProperty("username")]
        private string _username = string.Empty;

        [JsonProperty("uuid")]
        public string UUID { get; set; } = string.Empty;

        [JsonIgnore]
        public string Uuid => UUID;

        [JsonProperty("accountType")]
        public AccountType AccountType { get; set; } = AccountType.Offline;

        [JsonIgnore]
        public string AccountTypeDisplay => AccountType switch
        {
            AccountType.Microsoft => "Microsoft Account",
            AccountType.Offline => "Offline Profile",
            _ => "Offline"
        };

        [JsonIgnore]
        public string SkinPath => AccountType == AccountType.Microsoft ? Username : string.Empty;

        [JsonProperty("accessToken")]
        public string? AccessToken { get; set; }

        [JsonProperty("refreshToken")]
        public string? RefreshToken { get; set; }

        [JsonProperty("tokenExpiresAt")]
        public DateTime? TokenExpiresAt { get; set; }

        [JsonProperty("hasEntitlement")]
        public bool HasEntitlement { get; set; } = true;

        [JsonProperty("avatarIndex")]
        public int AvatarIndex { get; set; }

        [JsonProperty("lastVersion")]
        public string LastVersion { get; set; } = string.Empty;

        [JsonProperty("createdAt")]
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

        [ObservableProperty]
        [JsonProperty("isActive")]
        private bool _isActive;
    }
}
