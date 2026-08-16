using System;
using System.Collections.Generic;
using Newtonsoft.Json;

namespace VayuClient.Models
{
    public class DeviceCodeResponse
    {
        [JsonProperty("device_code")]
        public string DeviceCode { get; set; } = string.Empty;

        [JsonProperty("user_code")]
        public string UserCode { get; set; } = string.Empty;

        [JsonProperty("verification_uri")]
        public string VerificationUri { get; set; } = "https://microsoft.com/link";

        [JsonProperty("expires_in")]
        public int ExpiresIn { get; set; } = 900;

        [JsonProperty("interval")]
        public int Interval { get; set; } = 5;

        [JsonProperty("message")]
        public string Message { get; set; } = string.Empty;
    }

    public class MsaTokenResponse
    {
        [JsonProperty("access_token")]
        public string AccessToken { get; set; } = string.Empty;

        [JsonProperty("refresh_token")]
        public string? RefreshToken { get; set; }

        [JsonProperty("expires_in")]
        public int ExpiresIn { get; set; }

        [JsonProperty("token_type")]
        public string TokenType { get; set; } = "Bearer";

        [JsonProperty("error")]
        public string? Error { get; set; }
    }

    public class XboxAuthResponse
    {
        [JsonProperty("Token")]
        public string Token { get; set; } = string.Empty;

        [JsonProperty("DisplayClaims")]
        public XboxDisplayClaims? DisplayClaims { get; set; }
    }

    public class XboxDisplayClaims
    {
        [JsonProperty("xui")]
        public List<XboxXui>? Xui { get; set; }
    }

    public class XboxXui
    {
        [JsonProperty("uhs")]
        public string Uhs { get; set; } = string.Empty;
    }

    public class MinecraftAuthResponse
    {
        [JsonProperty("access_token")]
        public string AccessToken { get; set; } = string.Empty;

        [JsonProperty("expires_in")]
        public int ExpiresIn { get; set; }
    }

    public class MinecraftProfileResponse
    {
        [JsonProperty("id")]
        public string Id { get; set; } = string.Empty;

        [JsonProperty("name")]
        public string Name { get; set; } = string.Empty;
    }

    public class MinecraftEntitlementsResponse
    {
        [JsonProperty("items")]
        public List<MinecraftEntitlementItem>? Items { get; set; }
    }

    public class MinecraftEntitlementItem
    {
        [JsonProperty("name")]
        public string Name { get; set; } = string.Empty;
    }
}
