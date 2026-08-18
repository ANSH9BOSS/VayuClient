using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;

namespace VayuClient.Services.Authentication
{
    public class MicrosoftAuthService : IMicrosoftAuthService
    {
        // Official Mojang / Minecraft Live OAuth Public Client ID
        private const string MojangClientId = "00000000402b5328";
        private const string LiveScope = "service::user.auth.xboxlive.com::MBI_SSL";
        private const string DeviceCodeEndpoint = "https://login.live.com/oauth20_connect.srf";
        private const string TokenEndpoint = "https://login.live.com/oauth20_token.srf";

        private const string XboxAuthEndpoint = "https://user.auth.xboxlive.com/user/authenticate";
        private const string XstsAuthEndpoint = "https://xsts.auth.xboxlive.com/xsts/authorize";
        private const string MinecraftLoginEndpoint = "https://api.minecraftservices.com/authentication/login_with_xbox";
        private const string MinecraftStoreEndpoint = "https://api.minecraftservices.com/entitlements/mcstore";
        private const string MinecraftProfileEndpoint = "https://api.minecraftservices.com/minecraft/profile";

        private readonly string _activeClientId = MojangClientId;
        private string _activeTokenEndpoint = TokenEndpoint;

        private static readonly HttpClient _http = new()
        {
            Timeout = TimeSpan.FromSeconds(25)
        };

        static MicrosoftAuthService()
        {
            if (!_http.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _http.DefaultRequestHeaders.Add("User-Agent", AppInfo.UserAgent);
            }
        }

        public (bool IsValid, string Message) ValidateConfiguration()
        {
            if (string.IsNullOrWhiteSpace(_activeClientId))
            {
                return (false, "Missing Microsoft Client ID configuration.");
            }
            return (true, "Valid");
        }

        public async Task<DeviceCodeResponse> RequestDeviceCodeAsync(CancellationToken ct = default)
        {
            CrashLogger.LogMessage("[MicrosoftAuth] Starting Microsoft Live device code authentication flow");

            var content = new FormUrlEncodedContent(new[]
            {
                new KeyValuePair<string, string>("client_id", _activeClientId),
                new KeyValuePair<string, string>("scope", LiveScope),
                new KeyValuePair<string, string>("response_type", "device_code")
            });

            var response = await _http.PostAsync(DeviceCodeEndpoint, content, ct);
            var json = await response.Content.ReadAsStringAsync(ct);

            if (response.IsSuccessStatusCode)
            {
                var deviceCode = JsonConvert.DeserializeObject<DeviceCodeResponse>(json);
                if (deviceCode != null && !string.IsNullOrEmpty(deviceCode.DeviceCode) && !string.IsNullOrEmpty(deviceCode.UserCode))
                {
                    _activeTokenEndpoint = TokenEndpoint;
                    CrashLogger.LogMessage($"[MicrosoftAuth] Device code generated successfully. User Code: {deviceCode.UserCode}, URL: {deviceCode.VerificationUri}");
                    return deviceCode;
                }
            }

            string err = ParseOAuthError(json);
            CrashLogger.LogMessage($"[MicrosoftAuth] Device code request failed: {err}");
            throw new InvalidOperationException($"Microsoft device code request failed: {err}");
        }

        public async Task<UserProfile> PollForAuthenticationAsync(
            DeviceCodeResponse deviceCode,
            IProgress<string>? status = null,
            CancellationToken ct = default)
        {
            if (deviceCode == null || string.IsNullOrEmpty(deviceCode.DeviceCode))
            {
                throw new ArgumentException("Device code response is invalid.", nameof(deviceCode));
            }

            int interval = Math.Max(5, deviceCode.Interval);
            var expiry = DateTime.UtcNow.AddSeconds(deviceCode.ExpiresIn > 0 ? deviceCode.ExpiresIn : 900);

            status?.Report("Waiting for browser authorization (enter code at microsoft.com/link)...");

            while (DateTime.UtcNow < expiry && !ct.IsCancellationRequested)
            {
                await Task.Delay(interval * 1000, ct);

                var content = new FormUrlEncodedContent(new[]
                {
                    new KeyValuePair<string, string>("client_id", _activeClientId),
                    new KeyValuePair<string, string>("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                    new KeyValuePair<string, string>("device_code", deviceCode.DeviceCode)
                });

                HttpResponseMessage response;
                string json;
                try
                {
                    response = await _http.PostAsync(_activeTokenEndpoint, content, ct);
                    json = await response.Content.ReadAsStringAsync(ct);
                }
                catch (HttpRequestException)
                {
                    continue; // Transient network retry
                }

                if (response.IsSuccessStatusCode)
                {
                    var msaToken = JsonConvert.DeserializeObject<MsaTokenResponse>(json);
                    if (msaToken != null && !string.IsNullOrEmpty(msaToken.AccessToken))
                    {
                        CrashLogger.LogMessage("[MicrosoftAuth] Microsoft identity token acquired successfully.");
                        return await CompleteMinecraftAuthenticationAsync(msaToken, status, ct);
                    }
                }
                else
                {
                    try
                    {
                        var errObj = JObject.Parse(json);
                        var error = errObj["error"]?.ToString();

                        if (error == "authorization_pending")
                        {
                            status?.Report("Waiting for you to enter the code in your browser...");
                            continue;
                        }
                        else if (error == "slow_down")
                        {
                            interval += 5;
                            continue;
                        }
                        else if (error == "expired_token" || error == "code_expired")
                        {
                            throw new TimeoutException("Device code expired. Please click Sign In to try again.");
                        }
                        else if (error == "access_denied")
                        {
                            throw new InvalidOperationException("Microsoft sign-in was cancelled or declined in browser.");
                        }
                        else
                        {
                            string detailedErr = ParseOAuthError(json);
                            throw new InvalidOperationException(detailedErr);
                        }
                    }
                    catch (JsonReaderException)
                    {
                        throw new InvalidOperationException($"Unexpected response from Microsoft token service: {json}");
                    }
                }
            }

            if (ct.IsCancellationRequested)
            {
                throw new OperationCanceledException("Microsoft sign-in cancelled by user.");
            }

            throw new TimeoutException("Microsoft sign-in timed out. Please try signing in again.");
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
            var xblRes = await _http.PostAsync(XboxAuthEndpoint, xblReq, ct);
            var xblJson = await xblRes.Content.ReadAsStringAsync(ct);

            if (!xblRes.IsSuccessStatusCode)
            {
                string xblError = ParseXboxError(xblJson);
                throw new InvalidOperationException($"Xbox Live authentication failed: {xblError}");
            }

            var xblObj = JsonConvert.DeserializeObject<XboxAuthResponse>(xblJson);
            var xblToken = xblObj?.Token;
            var uhs = xblObj?.DisplayClaims?.Xui?.FirstOrDefault()?.Uhs;

            if (string.IsNullOrEmpty(xblToken) || string.IsNullOrEmpty(uhs))
            {
                throw new InvalidOperationException("Failed to acquire valid Xbox Live security credentials.");
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
            var xstsRes = await _http.PostAsync(XstsAuthEndpoint, xstsReq, ct);
            var xstsJson = await xstsRes.Content.ReadAsStringAsync(ct);

            if (!xstsRes.IsSuccessStatusCode)
            {
                string xstsError = ParseXstsError(xstsJson);
                throw new InvalidOperationException(xstsError);
            }

            var xstsObj = JsonConvert.DeserializeObject<XboxAuthResponse>(xstsJson);
            var xstsToken = xstsObj?.Token;

            if (string.IsNullOrEmpty(xstsToken))
            {
                throw new InvalidOperationException("Failed to acquire XSTS authorization token from Xbox Live.");
            }

            // 3. Minecraft Services Login
            status?.Report("Authenticating with Minecraft Services...");
            var mcLoginPayload = new
            {
                identityToken = $"XBL3.0 x={uhs};{xstsToken}"
            };

            var mcLoginReq = new StringContent(JsonConvert.SerializeObject(mcLoginPayload), Encoding.UTF8, "application/json");
            var mcLoginRes = await _http.PostAsync(MinecraftLoginEndpoint, mcLoginReq, ct);
            var mcLoginJson = await mcLoginRes.Content.ReadAsStringAsync(ct);

            if (!mcLoginRes.IsSuccessStatusCode)
            {
                throw new InvalidOperationException($"Minecraft services authentication failed: {mcLoginJson}");
            }

            var mcAuth = JsonConvert.DeserializeObject<MinecraftAuthResponse>(mcLoginJson);
            if (mcAuth == null || string.IsNullOrEmpty(mcAuth.AccessToken))
            {
                throw new InvalidOperationException("Minecraft Services did not return a valid session token.");
            }

            // 4. Check Game Ownership Entitlement
            status?.Report("Verifying Minecraft ownership...");
            bool hasEntitlement = await VerifyGameOwnershipAsync(mcAuth.AccessToken, ct);

            // 5. Get Minecraft Profile
            status?.Report("Fetching Minecraft profile from Mojang...");
            using var profileReq = new HttpRequestMessage(HttpMethod.Get, MinecraftProfileEndpoint);
            profileReq.Headers.Authorization = new AuthenticationHeaderValue("Bearer", mcAuth.AccessToken);

            var profileRes = await _http.SendAsync(profileReq, ct);
            var profileJson = await profileRes.Content.ReadAsStringAsync(ct);

            if (!profileRes.IsSuccessStatusCode)
            {
                if (profileRes.StatusCode == System.Net.HttpStatusCode.NotFound)
                {
                    throw new InvalidOperationException("Minecraft Java Edition profile not found on this Microsoft account. Please create your Minecraft Java profile at minecraft.net first.");
                }
                throw new InvalidOperationException($"Failed to retrieve Minecraft profile: HTTP {(int)profileRes.StatusCode} - {profileJson}");
            }

            var profileObj = JsonConvert.DeserializeObject<MinecraftProfileResponse>(profileJson);
            if (profileObj == null || string.IsNullOrWhiteSpace(profileObj.Name) || string.IsNullOrWhiteSpace(profileObj.Id))
            {
                throw new InvalidOperationException("Mojang profile response was invalid or missing player name/UUID.");
            }

            string username = profileObj.Name;
            string uuid = profileObj.Id;

            CrashLogger.LogMessage($"[Authentication] Microsoft account authenticated successfully (Player: {username}, UUID: {uuid})");
            status?.Report($"Authenticated as {username}!");

            return new UserProfile
            {
                Id = Guid.NewGuid().ToString("N"),
                Username = username,
                UUID = uuid,
                AccountType = AccountType.Microsoft,
                AccessToken = mcAuth.AccessToken,
                RefreshToken = msaToken.RefreshToken,
                TokenExpiresAt = DateTime.UtcNow.AddSeconds(mcAuth.ExpiresIn > 0 ? mcAuth.ExpiresIn : 86400),
                HasEntitlement = hasEntitlement,
                IsActive = true,
                CreatedAt = DateTime.UtcNow
            };
        }

        public async Task<bool> VerifyGameOwnershipAsync(string mcAccessToken, CancellationToken ct = default)
        {
            try
            {
                using var req = new HttpRequestMessage(HttpMethod.Get, MinecraftStoreEndpoint);
                req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", mcAccessToken);

                var res = await _http.SendAsync(req, ct);
                if (res.IsSuccessStatusCode)
                {
                    var json = await res.Content.ReadAsStringAsync(ct);
                    var entitlements = JsonConvert.DeserializeObject<MinecraftEntitlementsResponse>(json);
                    if (entitlements?.Items != null && entitlements.Items.Any(i =>
                        i.Name.IndexOf("product_minecraft", StringComparison.OrdinalIgnoreCase) >= 0 ||
                        i.Name.IndexOf("game_minecraft", StringComparison.OrdinalIgnoreCase) >= 0))
                    {
                        return true;
                    }
                }
            }
            catch { }

            return false;
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
                    new KeyValuePair<string, string>("scope", LiveScope)
                });

                var response = await _http.PostAsync(_activeTokenEndpoint, content, ct);
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
            catch (Exception ex)
            {
                CrashLogger.LogMessage($"[MicrosoftAuth] Token refresh error: {ex.Message}");
            }

            return profile;
        }

        private static string ParseOAuthError(string json)
        {
            try
            {
                var obj = JObject.Parse(json);
                var desc = obj["error_description"]?.ToString();
                if (!string.IsNullOrEmpty(desc)) return desc;
                var err = obj["error"]?.ToString();
                if (!string.IsNullOrEmpty(err)) return err;
            }
            catch { }
            return json;
        }

        private static string ParseXboxError(string json)
        {
            try
            {
                var obj = JObject.Parse(json);
                var errCode = obj["XErr"]?.ToString();
                if (errCode == "2148916233") return "This account does not have an Xbox profile. Please create an Xbox profile on xbox.com.";
                if (errCode == "2148916238") return "This account is a child account and must be added to a Family Safety group by a parent.";
            }
            catch { }
            return "Xbox Live user authentication failed.";
        }

        private static string ParseXstsError(string json)
        {
            try
            {
                var obj = JObject.Parse(json);
                var errCode = obj["XErr"]?.ToString();
                if (errCode == "2148916233") return "This account does not have an Xbox profile. Please create one on xbox.com.";
                if (errCode == "2148916235") return "Xbox Live is not available in your country/region.";
                if (errCode == "2148916238") return "This account is under 18 and requires adult parental permission.";
            }
            catch { }
            return "Xbox Live security token error.";
        }
    }
}
