using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Identity.Client;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using VayuClient.Core;
using VayuClient.Models;

namespace VayuClient.Services.Authentication
{
    public class MicrosoftAuthService : IMicrosoftAuthService
    {
        private static readonly HttpClient _http = new()
        {
            Timeout = TimeSpan.FromSeconds(25)
        };

        private IPublicClientApplication? _msalApp;

        static MicrosoftAuthService()
        {
            if (!_http.DefaultRequestHeaders.Contains("User-Agent"))
            {
                _http.DefaultRequestHeaders.Add("User-Agent", AppInfo.UserAgent);
            }
        }

        private IPublicClientApplication GetMsalApp()
        {
            if (_msalApp == null)
            {
                _msalApp = PublicClientApplicationBuilder.Create(MicrosoftAuthConfig.ClientId)
                    .WithAuthority(MicrosoftAuthConfig.Authority)
                    .WithRedirectUri(MicrosoftAuthConfig.RedirectUri)
                    .WithLogging((level, message, containsPii) =>
                    {
                        if (!containsPii && !string.IsNullOrWhiteSpace(message))
                        {
                            CrashLogger.LogMessage($"[MSAL] {message}");
                        }
                    }, LogLevel.Info, enablePiiLogging: false, enableDefaultPlatformLogging: false)
                    .Build();
            }
            return _msalApp;
        }

        public (bool IsValid, string Message) ValidateConfiguration()
        {
            if (string.IsNullOrWhiteSpace(MicrosoftAuthConfig.ClientId))
            {
                return (false, "Missing Microsoft Client ID configuration.");
            }
            if (string.IsNullOrWhiteSpace(MicrosoftAuthConfig.Authority))
            {
                return (false, "Missing Microsoft Authority configuration.");
            }
            return (true, "Valid");
        }

        /// <summary>
        /// Interactive Microsoft login via MSAL.NET system default browser.
        /// Strategy: silent cache → interactive browser → device code fallback.
        /// Scope is XboxLive.signin only — MSAL manages offline_access/openid internally.
        /// </summary>
        public async Task<UserProfile> LoginInteractiveAsync(IProgress<string>? status = null, CancellationToken ct = default)
        {
            CrashLogger.LogMessage("[AUTH] Starting Microsoft authentication. Strategy: silent → interactive → device-code");
            var app = GetMsalApp();

            // 1. Try silent token acquisition from MSAL cache
            var accounts = await app.GetAccountsAsync();
            if (accounts.Any())
            {
                try
                {
                    status?.Report("Refreshing Microsoft session from cache...");
                    var silentResult = await app.AcquireTokenSilent(MicrosoftAuthConfig.Scopes, accounts.First())
                        .ExecuteAsync(ct);

                    if (silentResult != null && !string.IsNullOrWhiteSpace(silentResult.AccessToken))
                    {
                        CrashLogger.LogMessage($"[AUTH] Silent token acquired for cached account: {silentResult.Account?.Username}");
                        return await CompleteMinecraftAuthenticationAsync(silentResult.AccessToken, silentResult.Account?.HomeAccountId?.Identifier, status, ct);
                    }
                }
                catch (MsalUiRequiredException)
                {
                    CrashLogger.LogMessage("[AUTH] Silent auth requires UI interaction. Proceeding to interactive flow.");
                }
                catch (Exception silentEx)
                {
                    CrashLogger.LogMessage($"[AUTH] Silent auth failed (non-critical): {silentEx.Message}");
                }
            }

            // 2. Interactive browser-based login
            try
            {
                status?.Report("Opening browser for Microsoft sign-in...");
                CrashLogger.LogMessage("[AUTH] Attempting interactive MSAL desktop browser auth...");

                var authResult = await app.AcquireTokenInteractive(MicrosoftAuthConfig.Scopes)
                    .WithPrompt(Prompt.SelectAccount)
                    .ExecuteAsync(ct);

                if (authResult != null && !string.IsNullOrWhiteSpace(authResult.AccessToken))
                {
                    CrashLogger.LogMessage($"[AUTH] Interactive token acquired for: {authResult.Account?.Username ?? "Microsoft User"}");
                    return await CompleteMinecraftAuthenticationAsync(authResult.AccessToken, authResult.Account?.HomeAccountId?.Identifier, status, ct);
                }
            }
            catch (MsalClientException ex) when (ex.ErrorCode == "authentication_canceled")
            {
                CrashLogger.LogMessage("[AUTH] Interactive login cancelled by user.");
                throw new OperationCanceledException("Microsoft sign-in was cancelled.", ex);
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (MsalException msalEx)
            {
                // Recoverable MSAL errors — fall through to device code
                CrashLogger.LogMessage($"[AUTH] Interactive MSAL auth failed ({msalEx.ErrorCode}): {msalEx.Message}. Falling back to device code flow...");
            }
            catch (Exception ex)
            {
                CrashLogger.LogMessage($"[AUTH] Interactive auth unexpected error: {ex.Message}. Falling back to device code flow...");
            }

            // 3. Device code fallback (works when redirect URI or browser fails)
            status?.Report("Browser login unavailable — opening device code sign-in...");
            CrashLogger.LogMessage("[AUTH] Falling back to device code authentication flow.");
            var deviceCode = await RequestDeviceCodeAsync(ct);

            // Report code to UI immediately
            status?.Report($"DEVICE_CODE:{deviceCode.UserCode}|{deviceCode.VerificationUri}");
            status?.Report($"Enter code: {deviceCode.UserCode} at {deviceCode.VerificationUri}");

            // Automatically open browser for convenience
            try
            {
                Process.Start(new ProcessStartInfo(deviceCode.VerificationUri) { UseShellExecute = true });
            }
            catch { }

            return await PollForAuthenticationAsync(deviceCode, status, ct);
        }


        public async Task<DeviceCodeResponse> RequestDeviceCodeAsync(CancellationToken ct = default)
        {
            CrashLogger.LogMessage("[MicrosoftAuth] Starting Microsoft device code authentication flow");

            var content = new FormUrlEncodedContent(new[]
            {
                new KeyValuePair<string, string>("client_id", "00000000402b5328"),
                new KeyValuePair<string, string>("scope", "service::user.auth.xboxlive.com::MBI_SSL"),
                new KeyValuePair<string, string>("response_type", "device_code")
            });

            var response = await _http.PostAsync("https://login.live.com/oauth20_connect.srf", content, ct);
            var json = await response.Content.ReadAsStringAsync(ct);

            if (response.IsSuccessStatusCode)
            {
                var deviceCode = JsonConvert.DeserializeObject<DeviceCodeResponse>(json);
                if (deviceCode != null && !string.IsNullOrEmpty(deviceCode.DeviceCode) && !string.IsNullOrEmpty(deviceCode.UserCode))
                {
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
                    new KeyValuePair<string, string>("client_id", "00000000402b5328"),
                    new KeyValuePair<string, string>("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                    new KeyValuePair<string, string>("device_code", deviceCode.DeviceCode)
                });

                HttpResponseMessage response;
                string json;
                try
                {
                    response = await _http.PostAsync("https://login.live.com/oauth20_token.srf", content, ct);
                    json = await response.Content.ReadAsStringAsync(ct);
                }
                catch (HttpRequestException)
                {
                    continue;
                }

                if (response.IsSuccessStatusCode)
                {
                    var msaToken = JsonConvert.DeserializeObject<MsaTokenResponse>(json);
                    if (msaToken != null && !string.IsNullOrEmpty(msaToken.AccessToken))
                    {
                        CrashLogger.LogMessage("[MicrosoftAuth] Microsoft identity token acquired successfully via device code.");
                        return await CompleteMinecraftAuthenticationAsync(msaToken.AccessToken, msaToken.RefreshToken, status, ct);
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
            string msAccessToken,
            string? refreshTokenOrAccountId,
            IProgress<string>? status,
            CancellationToken ct)
        {
            // 1. Xbox Live User Authentication
            status?.Report("Authenticating with Xbox Live...");
            CrashLogger.LogMessage("[AUTH] Requesting Xbox Live user token (user.auth.xboxlive.com)...");

            string[] ticketFormats = msAccessToken.StartsWith("d=")
                ? new[] { msAccessToken, msAccessToken.Substring(2) }
                : new[] { $"d={msAccessToken}", msAccessToken, $"t={msAccessToken}" };

            string xblJson = "";
            bool xblSuccess = false;
            HttpResponseMessage? lastXblRes = null;

            foreach (var ticket in ticketFormats)
            {
                var xblPayload = new
                {
                    Properties = new
                    {
                        AuthMethod = "RPS",
                        SiteName = "user.auth.xboxlive.com",
                        RpsTicket = ticket
                    },
                    RelyingParty = "http://auth.xboxlive.com",
                    TokenType = "JWT"
                };

                using var xblReq = new HttpRequestMessage(HttpMethod.Post, MicrosoftAuthConfig.XboxAuthEndpoint);
                xblReq.Content = new StringContent(JsonConvert.SerializeObject(xblPayload), Encoding.UTF8, "application/json");
                xblReq.Headers.Accept.Clear();
                xblReq.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
                xblReq.Headers.TryAddWithoutValidation("x-xbl-contract-version", "1");

                lastXblRes = await _http.SendAsync(xblReq, ct);
                xblJson = await lastXblRes.Content.ReadAsStringAsync(ct);

                if (lastXblRes.IsSuccessStatusCode)
                {
                    xblSuccess = true;
                    break;
                }
                CrashLogger.LogMessage($"[AUTH] Xbox Live attempt format failed (HTTP {(int)lastXblRes.StatusCode}): {xblJson}");
            }

            if (!xblSuccess)
            {
                string xblError = ParseXboxError(xblJson);
                CrashLogger.LogMessage($"[AUTH] Xbox Live authentication failed: {xblError}");
                throw new InvalidOperationException($"Xbox Live authentication failed: {xblError}");
            }

            var xblObj = JsonConvert.DeserializeObject<XboxAuthResponse>(xblJson);
            var xblToken = xblObj?.Token;
            var uhs = xblObj?.DisplayClaims?.Xui?.FirstOrDefault()?.Uhs;

            if (string.IsNullOrEmpty(xblToken) || string.IsNullOrEmpty(uhs))
            {
                throw new InvalidOperationException("Failed to acquire valid Xbox Live security credentials.");
            }

            CrashLogger.LogMessage("[AUTH] Xbox Live user token acquired successfully.");

            // 2. XSTS Security Token
            status?.Report("Acquiring XSTS Security token...");
            CrashLogger.LogMessage("[AUTH] Requesting XSTS security token for Minecraft Services (xsts.auth.xboxlive.com)...");

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

            using var xstsReq = new HttpRequestMessage(HttpMethod.Post, MicrosoftAuthConfig.XstsAuthEndpoint);
            xstsReq.Content = new StringContent(JsonConvert.SerializeObject(xstsPayload), Encoding.UTF8, "application/json");
            xstsReq.Headers.Accept.Clear();
            xstsReq.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
            xstsReq.Headers.TryAddWithoutValidation("x-xbl-contract-version", "1");

            var xstsRes = await _http.SendAsync(xstsReq, ct);
            var xstsJson = await xstsRes.Content.ReadAsStringAsync(ct);

            if (!xstsRes.IsSuccessStatusCode)
            {
                string xstsError = ParseXstsError(xstsJson);
                CrashLogger.LogMessage($"[AUTH] XSTS authentication failed: {xstsError}");
                throw new InvalidOperationException(xstsError);
            }

            var xstsObj = JsonConvert.DeserializeObject<XboxAuthResponse>(xstsJson);
            var xstsToken = xstsObj?.Token;

            if (string.IsNullOrEmpty(xstsToken))
            {
                throw new InvalidOperationException("Failed to acquire XSTS authorization token from Xbox Live.");
            }

            CrashLogger.LogMessage("[AUTH] XSTS security token acquired successfully.");

            // 3. Minecraft Services Login
            status?.Report("Authenticating with Minecraft Services...");
            CrashLogger.LogMessage("[AUTH] Authenticating with Minecraft Services (api.minecraftservices.com)...");

            var mcLoginPayload = new
            {
                identityToken = $"XBL3.0 x={uhs};{xstsToken}"
            };

            using var mcLoginReq = new HttpRequestMessage(HttpMethod.Post, MicrosoftAuthConfig.MinecraftLoginEndpoint);
            mcLoginReq.Content = new StringContent(JsonConvert.SerializeObject(mcLoginPayload), Encoding.UTF8, "application/json");
            mcLoginReq.Headers.Accept.Clear();
            mcLoginReq.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

            var mcLoginRes = await _http.SendAsync(mcLoginReq, ct);
            var mcLoginJson = await mcLoginRes.Content.ReadAsStringAsync(ct);

            if (!mcLoginRes.IsSuccessStatusCode)
            {
                CrashLogger.LogMessage($"[AUTH] Minecraft Services authentication failed: {mcLoginJson}");
                throw new InvalidOperationException($"Minecraft services authentication failed: {mcLoginJson}");
            }

            var mcAuth = JsonConvert.DeserializeObject<MinecraftAuthResponse>(mcLoginJson);
            if (mcAuth == null || string.IsNullOrEmpty(mcAuth.AccessToken))
            {
                throw new InvalidOperationException("Minecraft Services did not return a valid session token.");
            }

            CrashLogger.LogMessage("[AUTH] Minecraft Services session token acquired successfully.");

            // 4. Check Game Ownership Entitlement
            status?.Report("Verifying Minecraft ownership...");
            bool hasEntitlement = await VerifyGameOwnershipAsync(mcAuth.AccessToken, ct);
            CrashLogger.LogMessage($"[AUTH] Minecraft game ownership verification: {(hasEntitlement ? "OWNED" : "UNCONFIRMED")}");

            // 5. Get Minecraft Profile
            status?.Report("Fetching Minecraft Java profile from Mojang...");
            CrashLogger.LogMessage("[AUTH] Fetching official Minecraft Java profile...");

            using var profileReq = new HttpRequestMessage(HttpMethod.Get, MicrosoftAuthConfig.MinecraftProfileEndpoint);
            profileReq.Headers.Authorization = new AuthenticationHeaderValue("Bearer", mcAuth.AccessToken);

            var profileRes = await _http.SendAsync(profileReq, ct);
            var profileJson = await profileRes.Content.ReadAsStringAsync(ct);

            if (!profileRes.IsSuccessStatusCode)
            {
                if (profileRes.StatusCode == System.Net.HttpStatusCode.NotFound)
                {
                    CrashLogger.LogMessage("[AUTH] Minecraft Java Edition profile NOT FOUND (HTTP 404).");
                    throw new InvalidOperationException("No Minecraft Java Edition profile found on this Microsoft account. Please verify that this account owns Minecraft Java Edition or create your player profile on minecraft.net first.");
                }
                CrashLogger.LogMessage($"[AUTH] Minecraft profile lookup failed: HTTP {(int)profileRes.StatusCode}");
                throw new InvalidOperationException($"Failed to retrieve Minecraft profile: HTTP {(int)profileRes.StatusCode} - {profileJson}");
            }

            var profileObj = JsonConvert.DeserializeObject<MinecraftProfileResponse>(profileJson);
            if (profileObj == null || string.IsNullOrWhiteSpace(profileObj.Name) || string.IsNullOrWhiteSpace(profileObj.Id))
            {
                throw new InvalidOperationException("Mojang profile response was invalid or missing player name/UUID.");
            }

            string username = profileObj.Name;
            string uuid = profileObj.Id;

            CrashLogger.LogMessage($"[AUTH] Official Minecraft Java profile resolved: Player={username}, UUID={uuid}");
            status?.Report($"Authenticated as {username}!");

            return new UserProfile
            {
                Id = Guid.NewGuid().ToString("N"),
                Username = username,
                UUID = uuid,
                AccountType = AccountType.Microsoft,
                AccessToken = mcAuth.AccessToken,
                RefreshToken = refreshTokenOrAccountId ?? string.Empty,
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
                using var req = new HttpRequestMessage(HttpMethod.Get, MicrosoftAuthConfig.MinecraftStoreEndpoint);
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
            catch (Exception ex)
            {
                CrashLogger.LogException("MicrosoftAuthService.VerifyGameOwnership", ex);
            }

            return false;
        }

        public async Task<UserProfile?> RefreshTokenAsync(UserProfile profile, CancellationToken ct = default)
        {
            if (profile.AccountType != AccountType.Microsoft)
            {
                return profile;
            }

            try
            {
                CrashLogger.LogMessage($"[AUTH] Refreshing session for player {profile.Username} via MSAL.NET...");
                var app = GetMsalApp();
                var accounts = await app.GetAccountsAsync();
                var account = accounts.FirstOrDefault(a => a.HomeAccountId.Identifier == profile.RefreshToken) 
                              ?? accounts.FirstOrDefault();

                AuthenticationResult? authResult = null;
                if (account != null)
                {
                    authResult = await app.AcquireTokenSilent(MicrosoftAuthConfig.Scopes, account).ExecuteAsync(ct);
                }

                if (authResult != null && !string.IsNullOrEmpty(authResult.AccessToken))
                {
                    var refreshed = await CompleteMinecraftAuthenticationAsync(authResult.AccessToken, authResult.Account?.HomeAccountId?.Identifier, null, ct);
                    refreshed.Id = profile.Id;
                    refreshed.AvatarIndex = profile.AvatarIndex;
                    return refreshed;
                }
            }
            catch (Exception ex)
            {
                CrashLogger.LogMessage($"[AUTH] Silent token refresh notice: {ex.Message}");
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
                if (string.IsNullOrWhiteSpace(json)) return "Xbox Live rejected the security token (empty response). Please ensure your Microsoft account has an Xbox profile created on xbox.com.";
                var obj = JObject.Parse(json);
                var errCode = obj["XErr"]?.ToString();
                if (errCode == "2148916233") return "This Microsoft account does not have an Xbox gamer profile. Please sign in to https://xbox.com once to create your free gamer tag.";
                if (errCode == "2148916235") return "Xbox Live is not available in your country/region.";
                if (errCode == "2148916238") return "This account is a child account and requires adult parental permission added via Xbox Family Safety.";
                var message = obj["Message"]?.ToString() ?? obj["error_description"]?.ToString() ?? obj["error"]?.ToString();
                if (!string.IsNullOrEmpty(message)) return $"{message} (Code: {errCode})";
            }
            catch { }
            return string.IsNullOrWhiteSpace(json) ? "Xbox Live user authentication failed." : json;
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
