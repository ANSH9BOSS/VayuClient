using System;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;

namespace VayuClient.Services.Authentication
{
    public interface IAuthenticationService
    {
        bool IsAuthenticated { get; }
        Task<UserProfile> LoginInteractiveAsync(IProgress<string>? progress = null, CancellationToken ct = default);
        Task<DeviceCodeResponse> BeginDeviceCodeLoginAsync(CancellationToken ct = default);
        Task<UserProfile> CompleteDeviceCodeLoginAsync(DeviceCodeResponse deviceCode, IProgress<string>? progress = null, CancellationToken ct = default);
        Task SignOutAsync(string? profileId = null);
    }
}
