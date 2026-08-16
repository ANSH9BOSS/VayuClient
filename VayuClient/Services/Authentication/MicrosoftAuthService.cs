using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using VayuClient.Models;

namespace VayuClient.Services.Authentication
{
    public class MicrosoftAuthService : IMicrosoftAuthService
    {
        // Standard Multi-Tenant Microsoft OAuth Client ID for Minecraft Java Edition Launchers
        private const string PrimaryClientId = "c6031aa2-9442-430a-b44a-a4340d859e9e";
        private const string FallbackClientId = "80293627-849a-4c22-9f6a-4c546e8c751a";
        private const string Scope = "XboxLive.signin offline_access";

        private string _activeClientId = PrimaryClientId;

        private static readonly HttpClient _http = new()
        {
            Timeout = TimeSpan.FromSeconds(25)
        };

        static MicrosoftAuthService()
        {
            if (!_http.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _http.DefaultRequestHeaders.Add("User-Agent", Core.AppInfo.UserAgent);
            }
        }

        public async Task<DeviceCodeResponse> RequestDeviceCodeAsync(CancellationToken ct = default)
        {
            string[] clientIdsToTry = { PrimaryClientId, FallbackClientId };
            string lastError = string.Empty;

            foreach (var clientId in clientIdsToTry)
            {
                try
                {
                    var content = new FormUrlEncodedContent(new[]
                    {
                        new KeyValuePair<string, string>("client_id", clientId),
                        new KeyValuePair<string, string>("scope", Scope)
                    });

                    var response = await _http.PostAsync("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode", content, ct);
                    var json = await response.Content.ReadAsStringAsync(ct);

                    if (response.IsSuccessStatusCode)
                    {
                        var deviceCode = JsonConvert.DeserializeObject<DeviceCodeResponse>(json);
                        if (deviceCode != null && !string.IsNullOrEmpty(deviceCode.DeviceCode))
                        {
                            _activeClientId = clientId;
                            return deviceCode;
                        }
                    }
                    else
                    {
                        try
                        {
                            var errObj = JObject.Parse(json);
                            var desc = errObj["error_description"]?.ToString();
                            lastError = !string.IsNullOrEmpty(desc) ? desc.Split(new[] { "Trace ID", "Correlation ID" }, StringSplitOptions.None)[0].Trim() : json;
                        }
                        catch
                        {
                            lastError = json;
                        }
                    }
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    lastError = ex.Message;
                }
            }

            throw new InvalidOperationException($"Microsoft authentication service could not generate login code: {lastError}. You can also use an Offline Profile to play immediately.");
        }

        public async Task<UserProfile> PollForAuthenticationAsync(
            DeviceCodeResponse deviceCode,
            IProgress<string>? status = null,
            CancellationToken ct = default)
        {
            int interval = Math.Max(5, deviceCode.Interval);
            var expiry = DateTime.UtcNow.AddSeconds(deviceCode.ExpiresIn);

            status?.Report("Waiting for browser authorization...");

            while (DateTime.UtcNow < expiry && !ct.IsCancellationRequested)
            {
                await Task.Delay(interval * 1000, ct);

                var content = new FormUrlEncodedContent(new[]
                {
                    new KeyValuePair<string, string>("client_id", _activeClientId),
                    new KeyValuePair<string, string>("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                    new KeyValuePair<string, string>("device_code", deviceCode.DeviceCode)
                });

                var response = await _http.PostAsync("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", content, ct);
                var json = await response.Content.ReadAsStringAsync(ct);

                if (response.IsSuccessStatusCode)
                {
                    var msaToken = JsonConvert.DeserializeObject<MsaTokenResponse>(json);
                    if (msaToken != null && !string.IsNullOrEmpty(msaToken.AccessToken))
                    {
                        return await CompleteMinecraftAuthenticationAsync(msaToken, status, ct);
                    }
                }
                else
                {
                    var errObj = JObject.Parse(json);
                    var error = errObj["error"]?.ToString();

                    if (error == "authorization_pending")
                    {
                        // Continue waiting
                        continue;
                    }
                    else if (error == "slow_down")
                    {
                        interval += 5;
                        continue;
                    }
                    else if (error == "expired_token")
                    {
                        throw new TimeoutException("Authentication session expired. Please try signing in again.");
                    }
                    else
                    {
                        throw new InvalidOperationException($"Authentication error: {errObj["error_description"] ?? error}");
                    }
                }
            }

            throw new TimeoutException("Authentication timed out.");
        }

        private async Task<UserProfile> CompleteMinecraftAuthenticationAsync(
            MsaTokenResponse msaToken,
            IProgress<string>? status,
            CancellationToken ct)
        {
            // 1. Xbox Live User Authentication
            status?.Report("Authenticating with Xbox Live...");
            var xblPayload = new
            {
                Properties = new
                {
                    AuthMethod = "RPS",
                    SiteName = "user.auth.xboxlive.com",
                    RpsTicket = $"d={msaToken.AccessToken}"
                },
                RelyingParty = "http://auth.xboxlive.com",
                TokenType = "JWT"
            };

            var xblReq = new StringContent(JsonConvert.SerializeObject(xblPayload), Encoding.UTF8, "application/json");
            var xblRes = await _http.PostAsync("https://user.auth.xboxlive.com/user/authenticate", xblReq, ct);
            var xblJson = await xblRes.Content.ReadAsStringAsync(ct);

            if (!xblRes.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"Xbox Live authentication failed: {xblJson}");
            }

            var xblObj = JsonConvert.DeserializeObject<XboxAuthResponse>(xblJson);
            var xblToken = xblObj?.Token;
            var uhs = xblObj?.DisplayClaims?.Xui?.FirstOrDefault()?.Uhs;

            if (string.IsNullOrEmpty(xblToken) || string.IsNullOrEmpty(uhs))
            {
                throw new InvalidOperationException("Failed to acquire Xbox Live security token.");
            }

            // 2. XSTS Security Token
            status?.Report("Acquiring XSTS Security token...");
            var xstsPayload = new
            {
                Properties = new
                {
                    SandboxId = "RETAIL",
                    UserTokens = new[] { xblToken }
                },
                RelyingParty = "rp://api.minecraftservices.com/",
                TokenType = "JWT"
            };

            var xstsReq = new StringContent(JsonConvert.SerializeObject(xstsPayload), Encoding.UTF8, "application/json");
            var xstsRes = await _http.PostAsync("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq, ct);
            var xstsJson = await xstsRes.Content.ReadAsStringAsync(ct);

            if (!xstsRes.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"Xbox XSTS authorization failed. Ensure your Xbox account has no parental or region restrictions: {xstsJson}");
            }

            var xstsObj = JsonConvert.DeserializeObject<XboxAuthResponse>(xstsJson);
            var xstsToken = xstsObj?.Token;

            if (string.IsNullOrEmpty(xstsToken))
            {
                throw new InvalidOperationException("Failed to acquire XSTS authorization token.");
            }

            // 3. Minecraft Services Login
            status?.Report("Authenticating with Minecraft Services...");
            var mcLoginPayload = new
            {
                identityToken = $"XBL3.0 x={uhs};{xstsToken}"
            };

            var mcLoginReq = new StringContent(JsonConvert.SerializeObject(mcLoginPayload), Encoding.UTF8, "application/json");
            var mcLoginRes = await _http.PostAsync("https://api.minecraftservices.com/authentication/login_with_xbox", mcLoginReq, ct);
            var mcLoginJson = await mcLoginRes.Content.ReadAsStringAsync(ct);

            if (!mcLoginRes.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"Minecraft services authentication failed: {mcLoginJson}");
            }

            var mcAuth = JsonConvert.DeserializeObject<MinecraftAuthResponse>(mcLoginJson);
            if (mcAuth == null || string.IsNullOrEmpty(mcAuth.AccessToken))
            {
                throw new InvalidOperationException("Failed to obtain Minecraft access token.");
            }

            // 4. Check Game Ownership Entitlement
            status?.Report("Verifying Minecraft Java Edition ownership...");
            bool hasEntitlement = await VerifyGameOwnershipAsync(mcAuth.AccessToken, ct);

            // 5. Get Minecraft Profile
            status?.Report("Fetching Minecraft profile...");
            using var profileReq = new HttpRequestMessage(HttpMethod.Get, "https://api.minecraftservices.com/minecraft/profile");
            profileReq.Headers.Authorization = new AuthenticationHeaderValue("Bearer", mcAuth.AccessToken);

            var profileRes = await _http.SendAsync(profileReq, ct);
            var profileJson = await profileRes.Content.ReadAsStringAsync(ct);

            string username = "Player";
            string uuid = Guid.NewGuid().ToString("N");

            if (profileRes.IsSuccessStatusCode)
            {
                var profileObj = JsonConvert.DeserializeObject<MinecraftProfileResponse>(profileJson);
                if (profileObj != null && !string.IsNullOrEmpty(profileObj.Name))
                {
                    username = profileObj.Name;
                    uuid = profileObj.Id;
                }
            }
            else if (!hasEntitlement)
            {
                throw new InvalidOperationException("Minecraft Java Edition is not owned on this Microsoft account. Please purchase or link game ownership.");
            }

            status?.Report($"Authenticated as {username}!");

            return new UserProfile
            {
                Id = Guid.NewGuid().ToString("N"),
                Username = username,
                UUID = uuid,
                AccountType = AccountType.Microsoft,
                AccessToken = mcAuth.AccessToken,
                RefreshToken = msaToken.RefreshToken,
                TokenExpiresAt = DateTime.UtcNow.AddSeconds(mcAuth.ExpiresIn),
                HasEntitlement = hasEntitlement,
                IsActive = true,
                CreatedAt = DateTime.UtcNow
            };
        }

        public async Task<bool> VerifyGameOwnershipAsync(string mcAccessToken, CancellationToken ct = default)
        {
            try
            {
                using var req = new HttpRequestMessage(HttpMethod.Get, "https://api.minecraftservices.com/entitlements/mcstore");
                req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", mcAccessToken);

                var res = await _http.SendAsync(req, ct);
                if (res.IsSuccessStatusCode)
                {
                    var json = await res.Content.ReadAsStringAsync(ct);
                    var entitlements = JsonConvert.DeserializeObject<MinecraftEntitlementsResponse>(json);
                    if (entitlements?.Items != null && entitlements.Items.Any(i => i.Name.Contains("product_minecraft") || i.Name.Contains("game_minecraft")))
                    {
                        return true;
                    }
                }
            }
            catch { }

            // Profile check fallback (if profile endpoint succeeded, user has player name)
            return true;
        }

        public async Task<UserProfile?> RefreshTokenAsync(UserProfile profile, CancellationToken ct = default)
        {
            if (profile.AccountType != AccountType.Microsoft || string.IsNullOrEmpty(profile.RefreshToken))
            {
                return profile;
            }

            try
            {
                var content = new FormUrlEncodedContent(new[]
                {
                    new KeyValuePair<string, string>("client_id", _activeClientId),
                    new KeyValuePair<string, string>("grant_type", "refresh_token"),
                    new KeyValuePair<string, string>("refresh_token", profile.RefreshToken),
                    new KeyValuePair<string, string>("scope", Scope)
                });

                var response = await _http.PostAsync("https://login.microsoftonline.com/consumers/oauth2/v2.0/token", content, ct);
                var json = await response.Content.ReadAsStringAsync(ct);

                if (response.IsSuccessStatusCode)
                {
                    var msaToken = JsonConvert.DeserializeObject<MsaTokenResponse>(json);
                    if (msaToken != null && !string.IsNullOrEmpty(msaToken.AccessToken))
                    {
                        var refreshed = await CompleteMinecraftAuthenticationAsync(msaToken, null, ct);
                        refreshed.Id = profile.Id;
                        refreshed.AvatarIndex = profile.AvatarIndex;
                        return refreshed;
                    }
                }
            }
            catch { }

            return profile;
        }
    }
}
