using System;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Authentication
{
    public interface IMicrosoftAuthService
    {
        Task<DeviceCodeResponse> RequestDeviceCodeAsync(CancellationToken ct = default);
        Task<UserProfile> PollForAuthenticationAsync(DeviceCodeResponse deviceCode, IProgress<string>? status = null, CancellationToken ct = default);
        Task<UserProfile?> RefreshTokenAsync(UserProfile profile, CancellationToken ct = default);
        Task<bool> VerifyGameOwnershipAsync(string mcAccessToken, CancellationToken ct = default);
    }
}
