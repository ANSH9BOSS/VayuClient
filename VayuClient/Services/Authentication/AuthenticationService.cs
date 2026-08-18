using System;
using System.Threading;
using System.Threading.Tasks;
using VayuClient.Models;
using VayuClient.Services.Account;
using VayuClient.Services.Profiles;

namespace VayuClient.Services.Authentication
{
    public class AuthenticationService : IAuthenticationService
    {
        private readonly IMicrosoftAuthService _msAuth;
        private readonly IProfileService _profileService;
        private readonly IAccountService _accountService;

        public bool IsAuthenticated => _accountService.ActiveProfile?.AccountType == AccountType.Microsoft;

        public AuthenticationService(IMicrosoftAuthService msAuth, IProfileService profileService, IAccountService accountService)
        {
            _msAuth = msAuth;
            _profileService = profileService;
            _accountService = accountService;
        }

        public async Task<UserProfile> LoginInteractiveAsync(IProgress<string>? progress = null, CancellationToken ct = default)
        {
            var profile = await _msAuth.LoginInteractiveAsync(progress, ct);
            _profileService.AddOrUpdateProfile(profile);
            _accountService.SetActiveProfile(profile.Id);
            return profile;
        }

        public async Task<DeviceCodeResponse> BeginDeviceCodeLoginAsync(CancellationToken ct = default)
        {
            return await _msAuth.RequestDeviceCodeAsync(ct);
        }

        public async Task<UserProfile> CompleteDeviceCodeLoginAsync(
            DeviceCodeResponse deviceCode,
            IProgress<string>? progress = null,
            CancellationToken ct = default)
        {
            var profile = await _msAuth.PollForAuthenticationAsync(deviceCode, progress, ct);
            _profileService.AddOrUpdateProfile(profile);
            _accountService.SetActiveProfile(profile.Id);
            return profile;
        }

        public Task SignOutAsync(string? profileId = null)
        {
            var targetId = profileId ?? _accountService.ActiveProfile?.Id;
            if (!string.IsNullOrEmpty(targetId))
            {
                _profileService.DeleteProfile(targetId);
                var remaining = _profileService.GetAllProfiles();
                if (remaining.Count > 0)
                {
                    _accountService.SetActiveProfile(remaining[0].Id);
                }
            }
            return Task.CompletedTask;
        }
    }
}
