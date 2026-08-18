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
        /// Interactive Microsoft login via system default browser and http://localhost loopback listener.
        /// Automatically opens browser, catches the Microsoft redirect, serves a styled success page, and continues the Minecraft pipeline.
        /// </summary>
        public async Task<UserProfile> LoginInteractiveAsync(IProgress<string>? status = null, CancellationToken ct = default)
        {
            CrashLogger.LogMessage("[AUTH] Starting browser loopback interactive OAuth authentication flow");
            status?.Report("Opening browser for Microsoft sign-in...");

            int port = GetRandomUnusedPort();
            string redirectUri = $"http://localhost:{port}/";
            string state = Guid.NewGuid().ToString("N");

            using var listener = new HttpListener();
            try
            {
                listener.Prefixes.Add(redirectUri);
                listener.Start();
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("MicrosoftAuthService.HttpListener.Start", ex);
                throw new InvalidOperationException($"Failed to start local browser listener on {redirectUri}: {ex.Message}", ex);
            }

            string authUrl = $"https://login.live.com/oauth20_authorize.srf?client_id={MicrosoftAuthConfig.MojangClientId}&response_type=code&scope=service::user.auth.xboxlive.com::MBI_SSL&redirect_uri={Uri.EscapeDataString(redirectUri)}&state={state}";

            try
            {
                Process.Start(new ProcessStartInfo(authUrl) { UseShellExecute = true });
            }
            catch (Exception ex)
            {
                CrashLogger.LogException("MicrosoftAuthService.BrowserLaunch", ex);
                throw new InvalidOperationException("Failed to launch system default browser for Microsoft authentication.", ex);
            }

            status?.Report("Waiting for you to complete sign-in in your browser...");

            string? authCode = null;

            using (ct.Register(() => { try { listener.Stop(); } catch { } }))
            {
                try
                {
                    var context = await listener.GetContextAsync();
                    var req = context.Request;
                    var res = context.Response;

                    string? returnedState = req.QueryString["state"];
                    authCode = req.QueryString["code"];
                    string? error = req.QueryString["error"];
                    string? errorDesc = req.QueryString["error_description"];

                    byte[] responseBuffer;
                    if (!string.IsNullOrEmpty(authCode))
                    {
                        string html = @"<!DOCTYPE html>
<html>
<head>
    <meta charset='utf-8'>
    <title>VayuClient - Sign In Successful</title>
    <style>
        body { background: #0b0f19; color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }
        .card { background: rgba(18, 24, 38, 0.9); border: 1px solid rgba(56, 189, 248, 0.3); border-radius: 16px; padding: 40px; text-align: center; max-width: 440px; box-shadow: 0 20px 40px rgba(0,0,0,0.6), 0 0 40px rgba(56, 189, 248, 0.2); }
        h1 { color: #38bdf8; font-size: 24px; margin: 0 0 12px; }
        p { color: #94a3b8; font-size: 15px; line-height: 1.6; margin: 0; }
        .badge { display: inline-block; background: rgba(56, 189, 248, 0.15); color: #38bdf8; padding: 6px 14px; border-radius: 20px; font-weight: 600; font-size: 13px; margin-bottom: 20px; }
    </style>
</head>
<body>
    <div class='card'>
        <div class='badge'>✓ Connected to VayuClient</div>
        <h1>Authentication Complete!</h1>
        <p>You have signed in successfully. You can close this tab and return to <strong>VayuClient</strong>.</p>
    </div>
</body>
</html>";
                        responseBuffer = Encoding.UTF8.GetBytes(html);
                        res.ContentType = "text/html; charset=utf-8";
                        res.ContentLength64 = responseBuffer.Length;
                        await res.OutputStream.WriteAsync(responseBuffer, 0, responseBuffer.Length);
                        res.OutputStream.Close();
                    }
                    else
                    {
                        string errHtml = $@"<!DOCTYPE html>
<html>
<head>
    <meta charset='utf-8'>
    <title>VayuClient - Sign In Failed</title>
    <style>
        body {{ background: #0b0f19; color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; }}
        .card {{ background: rgba(18, 24, 38, 0.9); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 16px; padding: 40px; text-align: center; max-width: 440px; }}
        h1 {{ color: #ef4444; font-size: 24px; margin: 0 0 12px; }}
        p {{ color: #94a3b8; font-size: 15px; line-height: 1.6; margin: 0; }}
    </style>
</head>
<body>
    <div class='card'>
        <h1>Sign In Failed</h1>
        <p>{errorDesc ?? error ?? "Sign-in was cancelled or declined."}</p>
    </div>
</body>
</html>";
                        responseBuffer = Encoding.UTF8.GetBytes(errHtml);
                        res.ContentType = "text/html; charset=utf-8";
                        res.ContentLength64 = responseBuffer.Length;
                        await res.OutputStream.WriteAsync(responseBuffer, 0, responseBuffer.Length);
                        res.OutputStream.Close();

                        throw new InvalidOperationException($"Microsoft login failed: {errorDesc ?? error ?? "Cancelled in browser"}");
                    }
                }
                catch (HttpListenerException) when (ct.IsCancellationRequested)
                {
                    throw new OperationCanceledException("Microsoft sign-in cancelled by user.");
                }
                finally
                {
                    try { listener.Stop(); } catch { }
                }
            }

            if (string.IsNullOrWhiteSpace(authCode))
            {
                throw new InvalidOperationException("Did not receive authorization code from Microsoft.");
            }

            status?.Report("Exchanging authorization code for identity tokens...");
            CrashLogger.LogMessage("[AUTH] Authorization code received from browser. Exchanging for tokens at login.live.com/oauth20_token.srf...");

            var tokenContent = new FormUrlEncodedContent(new[]
            {
                new KeyValuePair<string, string>("client_id", MicrosoftAuthConfig.MojangClientId),
                new KeyValuePair<string, string>("grant_type", "authorization_code"),
                new KeyValuePair<string, string>("code", authCode),
                new KeyValuePair<string, string>("redirect_uri", redirectUri),
                new KeyValuePair<string, string>("scope", "service::user.auth.xboxlive.com::MBI_SSL")
            });

            var tokenResponse = await _http.PostAsync(MicrosoftAuthConfig.LiveTokenEndpoint, tokenContent, ct);
            var tokenJson = await tokenResponse.Content.ReadAsStringAsync(ct);

            if (!tokenResponse.IsSuccessStatusCode)
            {
                string err = ParseOAuthError(tokenJson);
                CrashLogger.LogMessage($"[AUTH] Token exchange failed: {err}");
                throw new InvalidOperationException($"Microsoft token exchange failed: {err}");
            }

            var msaToken = JsonConvert.DeserializeObject<MsaTokenResponse>(tokenJson);
            if (msaToken == null || string.IsNullOrWhiteSpace(msaToken.AccessToken))
            {
                throw new InvalidOperationException("Microsoft token response was empty or invalid.");
            }

            CrashLogger.LogMessage("[AUTH] Microsoft identity token acquired successfully. Continuing through Xbox Live pipeline...");
            return await CompleteMinecraftAuthenticationAsync(msaToken.AccessToken, msaToken.RefreshToken, status, ct);
        }

        private static int GetRandomUnusedPort()
        {
            try
            {
                var listener = new System.Net.Sockets.TcpListener(System.Net.IPAddress.Loopback, 0);
                listener.Start();
                int port = ((System.Net.IPEndPoint)listener.LocalEndpoint).Port;
                listener.Stop();
                return port;
            }
            catch
            {
                return 28543;
            }
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

            var xblPayload = new
            {
                Properties = new
                {
                    AuthMethod = "RPS",
                    SiteName = "user.auth.xboxlive.com",
                    RpsTicket = $"d={msAccessToken}"
                },
                RelyingParty = "http://auth.xboxlive.com",
                TokenType = "JWT"
            };

            var xblReq = new StringContent(JsonConvert.SerializeObject(xblPayload), Encoding.UTF8, "application/json");
            var xblRes = await _http.PostAsync(MicrosoftAuthConfig.XboxAuthEndpoint, xblReq, ct);
            var xblJson = await xblRes.Content.ReadAsStringAsync(ct);

            if (!xblRes.IsSuccessStatusCode)
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

            var xstsReq = new StringContent(JsonConvert.SerializeObject(xstsPayload), Encoding.UTF8, "application/json");
            var xstsRes = await _http.PostAsync(MicrosoftAuthConfig.XstsAuthEndpoint, xstsReq, ct);
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

            var mcLoginReq = new StringContent(JsonConvert.SerializeObject(mcLoginPayload), Encoding.UTF8, "application/json");
            var mcLoginRes = await _http.PostAsync(MicrosoftAuthConfig.MinecraftLoginEndpoint, mcLoginReq, ct);
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
